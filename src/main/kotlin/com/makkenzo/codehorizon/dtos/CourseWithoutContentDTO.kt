package com.makkenzo.codehorizon.dtos

import com.makkenzo.codehorizon.models.CourseDifficultyLevels

data class CourseWithoutContentDTO(
    val title: String,
    val slug: String,
    val description: String?,
    val imagePreview: String?,
    val videoPreview: String?,
    val authorId: String,
    val lessons: List<LessonWithoutContent>,
    val rating: Double,
    val price: Double,
    val discount: Double,
    val difficulty: CourseDifficultyLevels,
    val category: String?,
    val videoLength: Double?
)

data class LessonWithoutContent(
    val slug: String,
    val title: String
)