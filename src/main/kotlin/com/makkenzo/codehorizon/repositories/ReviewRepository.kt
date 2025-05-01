package com.makkenzo.codehorizon.repositories

import com.makkenzo.codehorizon.models.Review
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository


interface ReviewRepository : MongoRepository<Review, String> {
    fun findByCourseId(courseId: String, pageable: Pageable): Page<Review>
    fun findByAuthorIdAndCourseId(authorId: String, courseId: String): Review?
    fun findAllByCourseId(courseId: String): List<Review>
    fun existsByAuthorIdAndCourseId(authorId: String, courseId: String): Boolean
    fun deleteByAuthorIdAndCourseId(authorId: String, courseId: String)
}