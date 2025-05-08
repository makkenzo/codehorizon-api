package com.makkenzo.codehorizon.repositories

import com.makkenzo.codehorizon.models.Course
import org.springframework.data.mongodb.repository.MongoRepository
import java.util.*

interface CourseRepository : MongoRepository<Course, String> {
    fun findByIdAndDeletedAtIsNull(id: String): Course?
    fun findBySlugAndDeletedAtIsNull(slug: String): Course?
    fun findByAuthorIdAndDeletedAtIsNull(authorId: String): List<Course>

    fun existsBySlug(slug: String): Boolean

    override fun findById(id: String): Optional<Course>
}