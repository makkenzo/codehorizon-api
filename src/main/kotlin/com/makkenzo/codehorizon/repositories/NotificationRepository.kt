package com.makkenzo.codehorizon.repositories

import com.makkenzo.codehorizon.models.Notification
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface NotificationRepository : MongoRepository<Notification, String> {
    fun findByUserIdOrderByCreatedAtDesc(userId: String, pageable: Pageable): Page<Notification>
    fun countByUserIdAndReadIsFalse(userId: String): Long
    fun findByUserIdAndReadIsFalse(userId: String): List<Notification>
}