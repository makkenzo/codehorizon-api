package com.makkenzo.codehorizon.models

import java.io.Serializable


enum class TaskType {
    TEXT_INPUT,
    CODE_INPUT,
    MULTIPLE_CHOICE
}

data class TestCase(
    val id: String,
    val name: String,
    val input: List<String>,
    val expectedOutput: List<String>,
    val isHidden: Boolean = false,
    val points: Int = 1
) : Serializable


data class Task(
    val id: String,
    val description: String,
    var solution: String? = null,

    val taskType: TaskType = TaskType.TEXT_INPUT,
    val options: List<String>? = null,

    val language: ProgrammingLanguage? = null,
    val boilerplateCode: String? = null,
    val testCases: List<TestCase> = emptyList(),

    val timeoutSeconds: Long? = null,
    val memoryLimitMb: Long? = null
) : Serializable