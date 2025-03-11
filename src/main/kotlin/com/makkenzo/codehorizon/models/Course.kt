package com.makkenzo.codehorizon.models

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "courses")
data class Course(
    @Id val id: String? = null,
    var title: String,
    var description: String,
    val imagePreview: String? = null,
    val videoPreview: String? = null,
    val authorId: String,
    var lessons: MutableList<Lesson> = mutableListOf(),
    var rating: Double = 0.0,
    var price: Double = 0.0,
    var discount: Double = 0.0,
    val difficulty: CourseDifficultyLevels,
)

enum class CourseDifficultyLevels {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED
}