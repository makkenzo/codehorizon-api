package com.makkenzo.codehorizon.models


data class Lesson(
    val id: String,
    var title: String,
    var content: String,
    var codeExamples: List<String> = emptyList(),
    var tasks: List<Task> = emptyList()
)
