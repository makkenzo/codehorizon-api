package com.makkenzo.codehorizon.dtos

import com.makkenzo.codehorizon.models.CourseDifficultyLevels
import java.io.Serializable

data class CourseDTO(
    val id: String,
    val title: String,
    val slug: String,
    val description: String?,
    val imagePreview: String?,
    val videoPreview: String?,
    val authorId: String,
    val authorName: String,
    val authorUsername: String,
    val rating: Double,
    val price: Double,
    val discount: Double,
    val isFree: Boolean = false,
    val difficulty: CourseDifficultyLevels,
    val category: String,
    val videoLength: Double = 0.0
) : Serializable