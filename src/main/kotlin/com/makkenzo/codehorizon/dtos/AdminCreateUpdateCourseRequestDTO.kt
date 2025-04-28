package com.makkenzo.codehorizon.dtos

import com.makkenzo.codehorizon.models.CourseDifficultyLevels

data class AdminCreateUpdateCourseRequestDTO(
    val title: String,
    val description: String?,
    val price: Double,
    val discount: Double? = 0.0,
    val difficulty: CourseDifficultyLevels,
    val category: String?,
    val authorId: String,
    val imagePreview: String? = null,
    val videoPreview: String? = null
)
