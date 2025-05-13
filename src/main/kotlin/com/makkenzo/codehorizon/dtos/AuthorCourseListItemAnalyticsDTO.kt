package com.makkenzo.codehorizon.dtos

data class AuthorCourseListItemAnalyticsDTO(
    val courseId: String,
    val courseTitle: String,
    val slug: String,
    val totalEnrolledStudents: Long,
    val averageCompletionRate: Double,
    val averageRating: Double,
    val imagePreview: String? = null
)
