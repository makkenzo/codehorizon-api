package com.makkenzo.codehorizon.repositories

import com.makkenzo.codehorizon.models.Course
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query

interface CourseRepository : MongoRepository<Course, String> {
    fun findByAuthorId(authorId: String): List<Course>
    fun findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
        title: String,
        description: String, pageable: Pageable
    ): Page<Course>

    fun existsBySlug(slug: String): Boolean

    @Query("{ 'lessons.slug':  ?0 }", exists = true)
    fun existsLessonWithSlug(slug: String): Boolean
    fun findBySlug(slug: String): Course?
}