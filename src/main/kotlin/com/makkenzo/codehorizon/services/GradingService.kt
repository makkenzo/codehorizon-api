package com.makkenzo.codehorizon.services

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.makkenzo.codehorizon.models.*
import com.makkenzo.codehorizon.repositories.SubmissionRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.*

@Service
class GradingService(
    private val submissionRepository: SubmissionRepository,
    private val objectMapper: ObjectMapper,
    private val dockerService: DockerService,
    @Value("classpath:runners/python_default_runner.py")
    private val pythonRunnerResource: Resource
) {
    private val logger = LoggerFactory.getLogger(GradingService::class.java)
    private val pythonRunnerScriptContent: String

    init {
        pythonRunnerScriptContent = try {
            pythonRunnerResource.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            logger.error("Не удалось загрузить скрипт python_default_runner.py: ${e.message}", e)
            throw IllegalStateException("Не удалось загрузить Python runner скрипт", e)
        }
    }

    @Async("taskExecutor")
    fun gradeSubmission(submissionFromRequest: Submission, task: Task) {
        logger.info("Начало проверки ответа ID: ${submissionFromRequest.id} для задачи ID: ${task.id}, тип: ${task.taskType}")

        val submissionOptional: Optional<Submission> = submissionRepository.findById(submissionFromRequest.id!!)

        if (!submissionOptional.isPresent) {
            logger.warn("Submission с ID ${submissionFromRequest.id} не найден перед началом проверки.")
            return
        }

        var currentSubmission: Submission = submissionOptional.get().copy(
            status = SubmissionStatus.CHECKING,
            checkedAt = Instant.now()
        )
        currentSubmission = submissionRepository.save(currentSubmission)

        var finalSubmissionState = currentSubmission

        try {
            when (task.taskType) {
                TaskType.CODE_INPUT -> {
                    if (currentSubmission.answerCode.isNullOrBlank()) {
                        finalSubmissionState = currentSubmission.copy(
                            status = SubmissionStatus.ERROR,
                            feedback = "Код ответа не может быть пустым.",
                            score = 0.0,
                            checkedAt = Instant.now()
                        )
                    } else if (task.language == ProgrammingLanguage.PYTHON) {
                        finalSubmissionState = gradePythonCode(currentSubmission, task, pythonRunnerScriptContent)
                    } else {
                        finalSubmissionState = currentSubmission.copy(
                            status = SubmissionStatus.ERROR,
                            feedback = "Проверка для языка ${task.language} еще не реализована.",
                            score = 0.0,
                            checkedAt = Instant.now()
                        )
                    }
                }

                TaskType.TEXT_INPUT -> {
                    val isCorrect =
                        task.solution?.trim()?.equals(currentSubmission.answerText?.trim(), ignoreCase = true) ?: false
                    finalSubmissionState = currentSubmission.copy(
                        status = if (isCorrect) SubmissionStatus.CORRECT else SubmissionStatus.INCORRECT,
                        score = if (isCorrect) 1.0 else 0.0,
                        feedback = if (isCorrect) "Ответ верный." else "Ответ неверный.",
                        checkedAt = Instant.now()
                    )
                }

                TaskType.MULTIPLE_CHOICE -> {
                    val isCorrect = task.solution == currentSubmission.answerText
                    finalSubmissionState = currentSubmission.copy(
                        status = if (isCorrect) SubmissionStatus.CORRECT else SubmissionStatus.INCORRECT,
                        score = if (isCorrect) 1.0 else 0.0,
                        feedback = if (isCorrect) "Выбран верный вариант." else "Выбран неверный вариант.",
                        checkedAt = Instant.now()
                    )
                }
            }
            submissionRepository.save(finalSubmissionState)
            logger.info("Проверка ответа ID: ${submissionFromRequest.id} завершена со статусом: ${finalSubmissionState.status}")
        } catch (e: Exception) {
            logger.error("Критическая ошибка во время проверки ответа ID: ${submissionFromRequest.id}", e)
            val errorSubmissionState = submissionRepository.findById(submissionFromRequest.id!!).map {
                it.copy(
                    status = SubmissionStatus.ERROR,
                    feedback = "Внутренняя ошибка сервера при проверке: ${e.message?.take(100)}",
                    checkedAt = Instant.now()
                )
            }.orElse(
                currentSubmission.copy(
                    status = SubmissionStatus.ERROR,
                    feedback = "Внутренняя ошибка сервера при проверке (не удалось обновить из БД): ${
                        e.message?.take(
                            100
                        )
                    }",
                    checkedAt = Instant.now()
                )
            )
            submissionRepository.save(errorSubmissionState)
        }
    }

    private fun gradePythonCode(submission: Submission, task: Task, testRunnerScriptContent: String): Submission {
        val studentCode = submission.answerCode ?: return submission.copy(
            status = SubmissionStatus.ERROR,
            feedback = "Код ответа отсутствует.",
            checkedAt = Instant.now()
        )

        val testCasesJson = try {
            objectMapper.writeValueAsString(task.testCases.map {
                mapOf(
                    "id" to it.id,
                    "name" to it.name,
                    "input" to it.input,
                    "expected_output" to it.expectedOutput
                )
            })
        } catch (e: Exception) {
            logger.error("Ошибка сериализации тест-кейсов для задачи ${task.id}: ${e.message}", e)
            return submission.copy(
                status = SubmissionStatus.ERROR,
                feedback = "Ошибка подготовки тестовых данных.",
                checkedAt = Instant.now()
            )
        }

        val dockerResult = dockerService.executePythonCode(
            studentCode = studentCode,
            testRunnerScript = testRunnerScriptContent,
            testCasesJsonContent = testCasesJson,
            timeoutSeconds = task.timeoutSeconds ?: 10L,
            memoryLimitMb = task.memoryLimitMb ?: 128L
        )

        var overallStatus = SubmissionStatus.ERROR
        var overallScore = 0.0
        var feedbackMessage = dockerResult.error ?: "Проверка завершена."
        val parsedTestResults = mutableListOf<TestRunResult>()
        var compileError: String? = null

        if (dockerResult.exitCode == 0 && dockerResult.error == null) {
            try {
                val fullOutputJson =
                    objectMapper.readValue(dockerResult.stdout, object : TypeReference<Map<String, Any?>>() {})
                compileError = fullOutputJson["compile_error"] as? String
                val testResultsList = fullOutputJson["test_results"] as? List<Map<String, Any?>>

                if (compileError != null) {
                    overallStatus = SubmissionStatus.ERROR
                    feedbackMessage = "Ошибка компиляции/импорта: $compileError"

                    task.testCases.forEach { tc ->
                        parsedTestResults.add(
                            TestRunResult(
                                testCaseId = tc.id,
                                testCaseName = tc.name,
                                passed = false,
                                actualOutput = null,
                                expectedOutput = tc.expectedOutput,
                                errorMessage = compileError,
                                executionTimeMs = 0
                            )
                        )
                    }
                } else if (testResultsList != null) {
                    var passedCount = 0
                    var totalPoints = 0
                    var earnedPoints = 0

                    for (resMap in testResultsList) {
                        val tcId = resMap["testCaseId"] as? String ?: "unknown_id"
                        val tc = task.testCases.find { it.id == tcId }
                        totalPoints += tc?.points ?: 0

                        val passed = resMap["passed"] as? Boolean ?: false
                        if (passed) {
                            passedCount++
                            earnedPoints += tc?.points ?: 0
                        }
                        parsedTestResults.add(
                            TestRunResult(
                                testCaseId = tcId,
                                testCaseName = resMap["testCaseName"] as? String ?: "Unknown Test",
                                passed = passed,
                                actualOutput = resMap["actualOutput"] as? List<String>,
                                expectedOutput = resMap["expectedOutput"] as? List<String>,
                                errorMessage = resMap["errorMessage"] as? String,
                                executionTimeMs = (resMap["executionTimeMs"] as? Number)?.toLong()
                            )
                        )
                    }

                    if (task.testCases.isNotEmpty()) {
                        overallScore = if (totalPoints > 0) earnedPoints.toDouble() / totalPoints.toDouble() else 0.0
                        overallStatus = when {
                            passedCount == task.testCases.size -> SubmissionStatus.CORRECT
                            passedCount > 0 -> SubmissionStatus.PARTIALLY_CORRECT
                            else -> SubmissionStatus.INCORRECT
                        }
                        feedbackMessage = "Пройдено ${passedCount} из ${task.testCases.size} тестов."
                    } else {
                        overallStatus = SubmissionStatus.CORRECT
                        overallScore = 1.0
                        feedbackMessage = "Код выполнен без ошибок (тест-кейсы отсутствуют)."
                    }
                } else {
                    feedbackMessage = "Не удалось разобрать результаты тестов из вывода Docker."
                    logger.warn("Некорректный JSON от Docker stdout: ${dockerResult.stdout}")
                }
            } catch (e: Exception) {
                logger.error(
                    "Ошибка парсинга JSON результатов от Docker: ${e.message}. \nStdout: ${dockerResult.stdout}\nStderr: ${dockerResult.stderr}",
                    e
                )
                feedbackMessage = "Ошибка обработки результатов проверки."
                overallStatus = SubmissionStatus.ERROR
            }
        } else if (dockerResult.error != null && dockerResult.error.contains("timed out")) {
            overallStatus = SubmissionStatus.TIMEOUT
            feedbackMessage = dockerResult.error
        } else {
            feedbackMessage = "Ошибка выполнения кода. " + (dockerResult.error ?: "")
            if (dockerResult.stderr.isNotBlank()) {
                feedbackMessage += "\nStderr: ${dockerResult.stderr.take(500)}"
            }
            overallStatus = SubmissionStatus.ERROR
        }

        return submission.copy(
            status = overallStatus,
            score = overallScore.coerceIn(0.0, 1.0),
            feedback = feedbackMessage,
            stdout = dockerResult.stdout.take(2000),
            stderr = dockerResult.stderr.take(2000),
            compileErrorMessage = compileError?.take(1000),
            testRunResults = parsedTestResults,
            checkedAt = Instant.now()
        )
    }
}