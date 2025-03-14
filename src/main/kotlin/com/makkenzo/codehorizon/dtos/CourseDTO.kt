package com.makkenzo.codehorizon.dtos

import com.makkenzo.codehorizon.models.CourseDifficultyLevels

data class CourseDTO(
    val id: String,
    val title: String,
    val description: String?,
    val imagePreview: String?,
    val videoPreview: String?,
    val authorId: String,
    val rating: Double,
    val price: Double,
    val discount: Double,
    val difficulty: CourseDifficultyLevels
)