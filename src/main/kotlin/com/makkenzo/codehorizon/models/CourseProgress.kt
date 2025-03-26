package com.makkenzo.codehorizon.models

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "course_progress")
data class CourseProgress(
    @Id val id: String? = null,
    val userId: String,
    val courseId: String,
    val completedLessons: List<String> = emptyList(),
    val progress: Double = 0.0,
    val lastUpdated: Instant = Instant.now()
)
