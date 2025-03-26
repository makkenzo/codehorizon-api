package com.makkenzo.codehorizon.repositories

import com.makkenzo.codehorizon.models.WishlistItem
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository


interface WishlistRepository : MongoRepository<WishlistItem, String> {
    fun findByUserId(userId: String, pageable: Pageable): Page<WishlistItem>
    fun existsByUserIdAndCourseId(userId: String, courseId: String): Boolean
    fun deleteByUserIdAndCourseId(userId: String, courseId: String)
}
