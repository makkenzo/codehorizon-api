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
import org.springframework.transaction.annotation.Transactional
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
    @Transactional
    fun gradeSubmission(submissionFromRequest: Submission, task: Task) {
        logger.info("Начало проверки ответа ID: ${submissionFromRequest.id} для задачи ID: ${task.id}, тип: ${task.taskType}")

        var currentSubmission = submissionRepository.findById(submissionFromRequest.id!!)
            .orElse(null) ?: run {
            logger.warn("Submission with ID ${submissionFromRequest.id} not found before starting grading.")
            return
        }

        if (currentSubmission.status != SubmissionStatus.PENDING && currentSubmission.status != SubmissionStatus.ERROR) {
            logger.warn("Submission ${currentSubmission.id} is already being processed or has been processed. Status: ${currentSubmission.status}. Skipping.")
        }

        currentSubmission = currentSubmission.copy(
            status = SubmissionStatus.CHECKING,
            checkedAt = Instant.now()
        )
        currentSubmission = submissionRepository.save(currentSubmission)

        var finalSubmissionState = currentSubmission

        try {
            finalSubmissionState = when (task.taskType) {
                TaskType.CODE_INPUT -> {
                    if (currentSubmission.answerCode.isNullOrBlank()) {
                        currentSubmission.copy(
                            status = SubmissionStatus.ERROR,
                            feedback = "Код ответа не может быть пустым.",
                            score = 0.0,
                            checkedAt = Instant.now()
                        )
                    } else if (task.language == ProgrammingLanguage.PYTHON) {
                        gradePythonCode(currentSubmission, task, pythonRunnerScriptContent)
                    } else {
                        currentSubmission.copy(
                            status = SubmissionStatus.ERROR,
                            feedback = "Проверка для языка ${task.language?.displayName ?: "неизвестного"} еще не реализована.",
                            score = 0.0,
                            checkedAt = Instant.now()
                        )
                    }
                }

                TaskType.TEXT_INPUT -> {
                    val isCorrect =
                        task.solution?.trim()?.equals(currentSubmission.answerText?.trim(), ignoreCase = true) ?: false
                    currentSubmission.copy(
                        status = if (isCorrect) SubmissionStatus.CORRECT else SubmissionStatus.INCORRECT,
                        score = if (isCorrect) 1.0 else 0.0,
                        feedback = if (isCorrect) "Ответ верный." else "Ответ неверный.",
                        checkedAt = Instant.now()
                    )
                }

                TaskType.MULTIPLE_CHOICE -> {
                    val isCorrect = task.solution == currentSubmission.answerText &&
                            (task.options?.contains(task.solution) == true)
                    currentSubmission.copy(
                        status = if (isCorrect) SubmissionStatus.CORRECT else SubmissionStatus.INCORRECT,
                        score = if (isCorrect) 1.0 else 0.0,
                        feedback = if (isCorrect) "Выбран верный вариант." else "Выбран неверный вариант.",
                        checkedAt = Instant.now()
                    )
                }
            }

            finalSubmissionState = submissionRepository.save(finalSubmissionState)
            logger.info("Grading for submission ID: ${submissionFromRequest.id} completed with status: ${finalSubmissionState.status}, score: ${finalSubmissionState.score}")

            if (finalSubmissionState.status == SubmissionStatus.CORRECT) {
                // Проверка, не завершил ли пользователь урок/курс этим ответом
            }

        } catch (e: Exception) {
            logger.error(
                "Critical error during grading submission ID: ${submissionFromRequest.id}. Task ID: ${task.id}",
                e
            )
            val submissionOnError = submissionRepository.findById(submissionFromRequest.id!!).orElse(currentSubmission)
            finalSubmissionState = submissionOnError.copy(
                status = SubmissionStatus.ERROR,
                feedback = "Внутренняя ошибка сервера при проверке: ${e.message?.take(150)}",
                checkedAt = Instant.now()
            )
            submissionRepository.save(finalSubmissionState)
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
                score = 0.0,
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

        var overallStatus: SubmissionStatus
        var overallScore = 0.0
        var feedbackMessage = dockerResult.error ?: "Проверка завершена."
        val parsedTestResults = mutableListOf<TestRunResult>()
        var compileError: String? = null

        if (dockerResult.error != null) {
            overallStatus = when {
                dockerResult.error.contains("timed out", ignoreCase = true) -> SubmissionStatus.TIMEOUT
                dockerResult.error.contains("Out of Memory", ignoreCase = true) -> SubmissionStatus.ERROR
                else -> SubmissionStatus.ERROR
            }
            feedbackMessage = dockerResult.error

            task.testCases.forEach { tc ->
                parsedTestResults.add(
                    TestRunResult(
                        testCaseId = tc.id,
                        testCaseName = tc.name,
                        passed = false,
                        actualOutput = null,
                        expectedOutput = tc.expectedOutput,
                        errorMessage = dockerResult.error,
                        executionTimeMs = dockerResult.executionTimeMs.takeIf { it > 0 }
                    )
                )
            }
        } else if (dockerResult.exitCode != 0 && dockerResult.stderr.isNotBlank() && dockerResult.stdout.isBlank()) {
            overallStatus = SubmissionStatus.ERROR
            compileError = dockerResult.stderr.take(1000)
            feedbackMessage = "Ошибка выполнения кода: ${compileError}"
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
        } else {
            try {
                val typeRef = object : TypeReference<Map<String, Any?>>() {}
                val fullOutputJson = objectMapper.readValue(dockerResult.stdout, typeRef)

                compileError = fullOutputJson["compile_error"] as? String
                @Suppress("UNCHECKED_CAST")
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
                    var totalPointsFromTests = 0
                    var earnedPointsFromTests = 0

                    for (resMap in testResultsList) {
                        val tcId = resMap["testCaseId"] as? String ?: "unknown_id_${UUID.randomUUID()}"
                        val tcDefinition = task.testCases.find { it.id == tcId }
                        val pointsForThisTest = tcDefinition?.points ?: 0
                        totalPointsFromTests += pointsForThisTest

                        val passed = resMap["passed"] as? Boolean ?: false
                        if (passed) {
                            passedCount++
                            earnedPointsFromTests += pointsForThisTest
                        }
                        @Suppress("UNCHECKED_CAST")
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
                        overallScore =
                            if (totalPointsFromTests > 0) earnedPointsFromTests.toDouble() / totalPointsFromTests.toDouble() else 0.0
                        overallStatus = when {
                            passedCount == task.testCases.size -> SubmissionStatus.CORRECT
                            passedCount > 0 -> SubmissionStatus.PARTIALLY_CORRECT
                            else -> SubmissionStatus.INCORRECT
                        }
                        feedbackMessage =
                            "Пройдено ${passedCount} из ${task.testCases.size} тестов. Набрано баллов: $earnedPointsFromTests из $totalPointsFromTests."
                    } else {
                        overallStatus = SubmissionStatus.CORRECT
                        overallScore = 1.0
                        feedbackMessage = "Код выполнен без ошибок (тест-кейсы отсутствуют)."
                    }
                } else {
                    feedbackMessage = "Не удалось разобрать результаты тестов из вывода Docker (stdout)."
                    logger.warn("Invalid JSON from Docker stdout: ${dockerResult.stdout.take(500)}")
                    overallStatus = SubmissionStatus.ERROR
                }
            } catch (e: Exception) {
                logger.error(
                    "Error parsing JSON results from Docker: ${e.message}. Stdout: ${dockerResult.stdout.take(500)}, Stderr: ${
                        dockerResult.stderr.take(
                            500
                        )
                    }",
                    e
                )
                feedbackMessage = "Ошибка обработки результатов проверки."
                overallStatus = SubmissionStatus.ERROR
            }
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