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
    private val pythonRunnerResource: Resource,
    @Value("classpath:runners/javascript_default_runner.js")
    private val javascriptRunnerResource: Resource,
    @Value("classpath:runners/run_java_tests.sh")
    private val javaRunnerScriptResource: Resource,
    @Value("classpath:runners/TestRunner.java")
    private val javaTestRunnerCodeResource: Resource
) {
    private val logger = LoggerFactory.getLogger(GradingService::class.java)

    private val pythonRunnerScriptContent: String
    private val javascriptRunnerScriptContent: String
    private val javaRunnerScriptContent: String
    private val javaTestRunnerCodeContent: String

    companion object {
        private const val MAX_ERROR_MESSAGE_LENGTH = 2048
        private const val MAX_STDOUT_STDERR_LENGTH = 2000
        private const val MAX_COMPILE_ERROR_LENGTH = 1000
    }

    init {
        pythonRunnerScriptContent = try {
            pythonRunnerResource.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            logger.error("Не удалось загрузить скрипт python_default_runner.py: ${e.message}", e)
            throw IllegalStateException("Не удалось загрузить Python runner скрипт", e)
        }
        javascriptRunnerScriptContent = try {
            javascriptRunnerResource.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            logger.error("Не удалось загрузить скрипт javascript_default_runner.js: ${e.message}", e)
            throw IllegalStateException("Не удалось загрузить JavaScript runner скрипт", e)
        }
        javaRunnerScriptContent = try {
            javaRunnerScriptResource.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            logger.error("Не удалось загрузить скрипт java_default_runner.sh: ${e.message}", e)
            throw IllegalStateException("Не удалось загрузить Java runner shell скрипт", e)
        }
        javaTestRunnerCodeContent = try {
            javaTestRunnerCodeResource.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            logger.error("Не удалось загрузить TestRunner.java: ${e.message}", e)
            throw IllegalStateException("Не удалось загрузить Java TestRunner код", e)
        }
    }

    @Async("taskExecutor")
    @Transactional
    fun gradeSubmission(submissionFromRequest: Submission, task: Task) {
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
                    } else {
                        when (task.language) {
                            ProgrammingLanguage.PYTHON -> gradeCodeWithDocker(
                                currentSubmission,
                                task,
                                pythonRunnerScriptContent,
                                ProgrammingLanguage.PYTHON
                            )

                            ProgrammingLanguage.JAVASCRIPT -> gradeCodeWithDocker(
                                currentSubmission,
                                task,
                                javascriptRunnerScriptContent,
                                ProgrammingLanguage.JAVASCRIPT
                            )

                            ProgrammingLanguage.JAVA -> {
                                gradeCodeWithDocker(
                                    submission = currentSubmission,
                                    task = task,
                                    runnerScriptContent = javaRunnerScriptContent,
                                    language = ProgrammingLanguage.JAVA,
                                    additionalFilesToCopy = mapOf("TestRunner.java" to javaTestRunnerCodeContent)
                                )
                            }

                            else -> {
                                currentSubmission.copy(
                                    status = SubmissionStatus.ERROR,
                                    feedback = "Проверка для языка ${task.language?.displayName ?: "неизвестного"} еще не реализована.",
                                    score = 0.0,
                                    checkedAt = Instant.now()
                                )
                            }
                        }
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

    private fun gradeCodeWithDocker(
        submission: Submission,
        task: Task,
        runnerScriptContent: String,
        language: ProgrammingLanguage,
        additionalFilesToCopy: Map<String, String> = emptyMap()
    ): Submission {
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
            logger.error(
                "Ошибка сериализации тест-кейсов для задачи ${task.id} (язык: ${language.displayName}): ${e.message}",
                e
            )
            return submission.copy(
                status = SubmissionStatus.ERROR,
                feedback = "Ошибка подготовки тестовых данных.",
                score = 0.0,
                checkedAt = Instant.now()
            )
        }

        val dockerImageName = when (language) {
            ProgrammingLanguage.PYTHON -> "codehorizon-python-runner:latest"
            ProgrammingLanguage.JAVASCRIPT -> "codehorizon-javascript-runner:latest"
            ProgrammingLanguage.JAVA -> "codehorizon-java-runner:latest"
        }
        val mainCodeFileName = when (language) {
            ProgrammingLanguage.PYTHON -> "student_code.py"
            ProgrammingLanguage.JAVASCRIPT -> "student_code.js"
            ProgrammingLanguage.JAVA -> "StudentCode.java"
        }
        val runnerFileName = when (language) {
            ProgrammingLanguage.PYTHON -> "python_default_runner.py"
            ProgrammingLanguage.JAVASCRIPT -> "javascript_default_runner.js"
            ProgrammingLanguage.JAVA -> "run_java_tests.sh"
        }
        val commandToRun = when (language) {
            ProgrammingLanguage.PYTHON -> listOf("python", runnerFileName)
            ProgrammingLanguage.JAVASCRIPT -> listOf("node", runnerFileName)
            ProgrammingLanguage.JAVA -> listOf("/bin/sh", runnerFileName)
        }

        val dockerResult = dockerService.executeCodeInContainer(
            imageName = dockerImageName,
            studentCodeContent = studentCode,
            studentCodeFileName = mainCodeFileName,
            testRunnerScriptContent = runnerScriptContent,
            testRunnerFileName = runnerFileName,
            testCasesJsonContent = testCasesJson,
            command = commandToRun,
            timeoutSeconds = task.timeoutSeconds ?: 20L,
            memoryLimitMb = task.memoryLimitMb ?: 256L,
            additionalFiles = additionalFilesToCopy
        )

        var overallStatus: SubmissionStatus
        var overallScore = 0.0
        var feedbackMessage = dockerResult.error ?: "Проверка завершена."
        val parsedTestResults = mutableListOf<TestRunResult>()
        var compileErrorFromJson: String? = null
        var runnerErrorFromJson: String? = null

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
        } else if (dockerResult.exitCode != 0 && dockerResult.stdout.isBlank() && dockerResult.stderr.isNotBlank()) {
            overallStatus = SubmissionStatus.ERROR
            compileErrorFromJson = dockerResult.stderr.take(MAX_ERROR_MESSAGE_LENGTH)
            feedbackMessage = "Ошибка выполнения кода: ${compileErrorFromJson}"
            task.testCases.forEach { tc ->
                parsedTestResults.add(
                    TestRunResult(
                        testCaseId = tc.id,
                        testCaseName = tc.name,
                        passed = false,
                        actualOutput = null,
                        expectedOutput = tc.expectedOutput,
                        errorMessage = compileErrorFromJson,
                        executionTimeMs = 0
                    )
                )
            }
        } else {
            try {
                val typeRef = object : TypeReference<Map<String, Any?>>() {}
                val fullOutputJson = objectMapper.readValue(dockerResult.stdout, typeRef)

                compileErrorFromJson = fullOutputJson["compile_error"] as? String
                runnerErrorFromJson = fullOutputJson["runner_error"] as? String
                @Suppress("UNCHECKED_CAST")
                val testResultsList = fullOutputJson["test_results"] as? List<Map<String, Any?>>

                if (compileErrorFromJson != null) {
                    overallStatus = SubmissionStatus.ERROR
                    feedbackMessage = "Ошибка в системе проверки: $runnerErrorFromJson"
                    task.testCases.forEach { tc ->
                        parsedTestResults.add(
                            TestRunResult(
                                testCaseId = tc.id,
                                testCaseName = tc.name,
                                passed = false,
                                actualOutput = null,
                                expectedOutput = tc.expectedOutput,
                                errorMessage = compileErrorFromJson,
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
                                errorMessage = (resMap["errorMessage"] as? String)?.substring(
                                    0,
                                    MAX_ERROR_MESSAGE_LENGTH.coerceAtMost((resMap["errorMessage"] as String).length)
                                ),
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
                    feedbackMessage = "Не удалось разобрать результаты тестов из вывода Docker (некорректный JSON)."
                    logger.warn("Invalid JSON structure from Docker stdout: ${dockerResult.stdout.take(500)}")
                    overallStatus = SubmissionStatus.ERROR
                }
            } catch (e: Exception) {
                logger.error(
                    "Error parsing JSON results from Docker: ${e.message}. Stdout: ${
                        dockerResult.stdout.take(
                            500
                        )
                    }, Stderr: ${dockerResult.stderr.take(500)}", e
                )
                feedbackMessage = "Ошибка обработки результатов проверки (невалидный JSON)."
                overallStatus = SubmissionStatus.ERROR
            }
        }

        return submission.copy(
            status = overallStatus,
            score = overallScore.coerceIn(0.0, 1.0),
            feedback = feedbackMessage,
            stdout = dockerResult.stdout.take(2000),
            stderr = dockerResult.stderr.take(2000),
            compileErrorMessage = compileErrorFromJson?.take(MAX_ERROR_MESSAGE_LENGTH)
                ?: dockerResult.stderr.takeIf { dockerResult.stdout.isBlank() && dockerResult.exitCode != 0 }
                    ?.take(MAX_ERROR_MESSAGE_LENGTH),
            testRunResults = parsedTestResults,
            checkedAt = Instant.now()
        )
    }
}