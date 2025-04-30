package com.makkenzo.codehorizon.models

data class Task(
    val id: String,
    val description: String,
    val solution: String,
    val tests: List<String> = emptyList(),
    val taskType: TaskType = TaskType.TEXT_INPUT,
    val options: List<String>? = null
)

enum class TaskType {
    TEXT_INPUT,
    CODE_INPUT,
    MULTIPLE_CHOICE
}