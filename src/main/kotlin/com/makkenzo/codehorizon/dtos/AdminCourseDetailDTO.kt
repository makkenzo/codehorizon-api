package com.makkenzo.codehorizon.dtos

import com.makkenzo.codehorizon.models.CourseDifficultyLevels
import com.makkenzo.codehorizon.models.Lesson

data class AdminCourseDetailDTO(
    val id: String,
    val title: String,
    val slug: String,
    val description: String?,
    val imagePreview: String?,
    val videoPreview: String?,
    val authorId: String,
    val authorUsername: String,
    val price: Double,
    val discount: Double,
    val difficulty: CourseDifficultyLevels,
    val category: String?,
    val videoLength: Double?,
    val lessons: List<Lesson>
)
