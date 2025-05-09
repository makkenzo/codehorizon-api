package com.makkenzo.codehorizon.dtos

import com.makkenzo.codehorizon.models.CourseDifficultyLevels

data class AdminCourseListItemDTO(
    val id: String,
    val title: String,
    val slug: String,
    val authorUsername: String,
    val price: Double,
    val discount: Double,
    val isFree: Boolean = false,
    val difficulty: CourseDifficultyLevels,
    val category: String?,
    val lessonCount: Int,
    val imagePreview: String? = null
)
