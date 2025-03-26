package com.makkenzo.codehorizon.repositories

import com.makkenzo.codehorizon.models.CourseProgress
import org.springframework.data.mongodb.repository.MongoRepository

interface CourseProgressRepository : MongoRepository<CourseProgress, String> {
    fun findByUserIdAndCourseId(userId: String, courseId: String): CourseProgress?
}