package com.makkenzo.codehorizon.dtos

data class AuthorCourseAnalyticsDTO(
    val courseId: String,
    val courseTitle: String,
    val totalEnrolledStudents: Long,
    val activeStudentsLast30Days: Long,
    val averageCompletionRate: Double,
    val averageRating: Double,
    val totalReviews: Long
)
