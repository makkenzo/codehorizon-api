package com.makkenzo.codehorizon.repositories

import com.makkenzo.codehorizon.models.CourseProgress
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import java.time.Instant

interface CourseProgressRepository : MongoRepository<CourseProgress, String> {
    fun findByUserIdAndCourseId(userId: String, courseId: String): CourseProgress?
    fun findByUserId(userId: String, pageable: Pageable): Page<CourseProgress>
    fun countByUserIdAndProgressGreaterThanEqual(userId: String, progress: Double): Int
    fun countByProgressGreaterThanEqual(progress: Double): Long
    fun existsByUserIdAndCourseId(userId: String, courseId: String): Boolean
    fun countByCourseId(courseId: String): Long
    fun findByCourseId(courseId: String): List<CourseProgress>
    fun countByCourseIdAndLastUpdatedAfter(courseId: String, date: Instant): Long
    fun findByUserIdAndProgressGreaterThanEqual(userId: String, progress: Double): List<CourseProgress>
    fun countByCourseIdAndProgressGreaterThanEqual(courseId: String, progress: Double): Int
    fun findByUserIdAndCompletedAtIsNotNullOrderByCompletedAtAsc(
        userId: String,
        pageable: Pageable
    ): List<CourseProgress>
}