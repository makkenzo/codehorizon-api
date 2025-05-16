package com.makkenzo.codehorizon.dtos

data class DockerExecutionResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val executionTimeMs: Long,
    val error: String? = null
)
