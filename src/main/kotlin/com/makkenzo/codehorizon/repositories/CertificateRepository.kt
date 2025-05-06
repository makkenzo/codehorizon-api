package com.makkenzo.codehorizon.repositories

import com.makkenzo.codehorizon.models.Certificate
import org.springframework.data.mongodb.repository.MongoRepository

interface CertificateRepository : MongoRepository<Certificate, String> {
    fun findByUserId(userId: String): List<Certificate>
    fun existsByUserIdAndCourseId(userId: String, courseId: String): Boolean
    fun findByUniqueCertificateId(uniqueCertificateId: String): Certificate?
}