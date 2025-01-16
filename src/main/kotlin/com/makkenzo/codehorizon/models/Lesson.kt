package com.makkenzo.codehorizon.models


data class Lesson(
    val id: String,
    val title: String,
    val content: String,
    val codeExamples: List<String> = emptyList(),
    val tasks: List<Task> = emptyList()
)
