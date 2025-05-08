package com.makkenzo.codehorizon.dtos

import java.time.Instant

data class StudentProgressDTO(
    val userId: String,
    val username: String,
    val email: String,
    val progressPercent: Double,
    val completedLessonsCount: Int,
    val totalLessonsCount: Int,
    val lastAccessedLessonAt: Instant?
)