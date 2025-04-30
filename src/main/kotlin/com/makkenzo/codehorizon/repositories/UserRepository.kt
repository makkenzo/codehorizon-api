package com.makkenzo.codehorizon.repositories

import com.makkenzo.codehorizon.models.User
import org.springframework.data.mongodb.repository.MongoRepository
import java.time.Instant

interface UserRepository : MongoRepository<User, String> {
    fun findByEmail(email: String): User?
    fun findByUsername(username: String): User?
    fun findByUsernameOrEmail(email: String, username: String): User?
    fun findByRefreshToken(refreshToken: String): User?
    fun countByCreatedAtBetween(start: Instant, end: Instant): Long
}