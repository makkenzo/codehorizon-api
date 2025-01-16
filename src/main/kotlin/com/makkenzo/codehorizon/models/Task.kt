package com.makkenzo.codehorizon.models

data class Task(
    val id: String,
    val description: String,
    val solution: String,
    val tests: List<String> = emptyList()
)