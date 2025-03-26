package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.models.CourseProgress
import com.makkenzo.codehorizon.repositories.CourseProgressRepository
import org.springframework.stereotype.Service

@Service
class CourseProgressService(private val courseProgressRepository: CourseProgressRepository) {
    fun addCourseProgress(userId: String, courseId: String): CourseProgress {
        val doc = courseProgressRepository.findByUserIdAndCourseId(userId, courseId)

        if (doc == null) {
            val newProgress = CourseProgress(userId = userId, courseId = courseId)
            courseProgressRepository.save(newProgress)
            return newProgress
        } else {
            throw Error("progress already exists")
        }
    }
}