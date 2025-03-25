package com.makkenzo.codehorizon.repositories

import com.makkenzo.codehorizon.models.Course
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository

interface CourseRepository : MongoRepository<Course, String> {
    fun findByAuthorId(authorId: String): List<Course>
    fun findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
        title: String,
        description: String, pageable: Pageable
    ): Page<Course>

    fun existsBySlug(slug: String): Boolean
}