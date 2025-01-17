package com.makkenzo.codehorizon.models

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "courses")
data class Course(
    @Id val id: String? = null,
    val title: String,
    val description: String,
    val authorId: String,
    val lessons: List<Lesson> = emptyList()
)