package com.makkenzo.codehorizon.repositories

import com.makkenzo.codehorizon.models.Course
import org.springframework.data.mongodb.repository.MongoRepository

interface CourseRepository : MongoRepository<Course, String> {
    fun findByAuthorId(authorId: String): List<Course>
    fun findByTitleContaining(title: String): List<Course>
}