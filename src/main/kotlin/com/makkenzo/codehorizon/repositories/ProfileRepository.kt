package com.makkenzo.codehorizon.repositories

import com.makkenzo.codehorizon.models.Profile
import org.springframework.data.mongodb.repository.MongoRepository

interface ProfileRepository : MongoRepository<Profile, String> {
    fun findByUserId(userId: String): Profile?
}