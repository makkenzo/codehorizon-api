package com.makkenzo.codehorizon.repositories

import com.makkenzo.codehorizon.models.ApplicationStatus
import com.makkenzo.codehorizon.models.MentorshipApplication
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository

interface MentorshipApplicationRepository : MongoRepository<MentorshipApplication, String> {
    fun findByUserId(userId: String): MentorshipApplication?
    fun existsByUserIdAndStatus(userId: String, status: ApplicationStatus): Boolean
    fun findByStatus(status: ApplicationStatus, pageable: Pageable): Page<MentorshipApplication>
}