package com.makkenzo.codehorizon.repositories

import com.makkenzo.codehorizon.models.User
import org.springframework.data.mongodb.repository.MongoRepository

interface UserRepository : MongoRepository<User, String> {
    fun findByEmail(email: String): User?

    fun findByRefreshToken(refreshToken: String): User?
}