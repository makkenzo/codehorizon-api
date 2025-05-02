package com.makkenzo.codehorizon.dtos

import com.makkenzo.codehorizon.models.CourseDifficultyLevels
import java.io.Serializable

data class CourseWithoutContentDTO(
    val id: String,
    val title: String,
    val slug: String,
    val description: String?,
    val imagePreview: String?,
    val videoPreview: String?,
    val authorName: String?,
    val authorUsername: String,
    val lessons: List<LessonWithoutContent>,
    val rating: Double,
    val price: Double,
    val discount: Double,
    val difficulty: CourseDifficultyLevels,
    val category: String?,
    val videoLength: Double?
) : Serializable

data class LessonWithoutContent(
    val slug: String,
    val title: String
) : Serializable