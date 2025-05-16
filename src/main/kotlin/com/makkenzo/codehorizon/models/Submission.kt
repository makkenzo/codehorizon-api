package com.makkenzo.codehorizon.models

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.io.Serializable
import java.time.Instant

enum class SubmissionStatus {
    PENDING,
    CHECKING,
    CORRECT,
    INCORRECT,
    PARTIALLY_CORRECT,
    ERROR,
    TIMEOUT,
    MANUAL_REVIEW_REQUIRED
}

data class TestRunResult(
    val testCaseId: String,
    val testCaseName: String,
    val passed: Boolean,
    val actualOutput: List<String>?,
    val expectedOutput: List<String>?,
    val errorMessage: String?,
    val executionTimeMs: Long?
)

@Document(collection = "submissions")
data class Submission(
    @Id val id: String? = null,
    @Indexed
    val userId: String,
    @Indexed
    val courseId: String,
    @Indexed
    val lessonId: String,
    @Indexed
    val taskId: String,

    val submittedAt: Instant = Instant.now(),
    var checkedAt: Instant? = null,

    val language: String?,
    val answerCode: String?,
    val answerText: String?,

    var status: SubmissionStatus = SubmissionStatus.PENDING,
    var score: Double? = null,
    var feedback: String? = null,

    var stdout: String? = null,
    var stderr: String? = null,
    var compileErrorMessage: String? = null,

    var testRunResults: List<TestRunResult> = emptyList()
) : Serializable