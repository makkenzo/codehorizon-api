package com.makkenzo.codehorizon.models

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

enum class ApplicationStatus {
    PENDING,
    APPROVED,
    REJECTED
}

@Document(collection = "mentorship_applications")
data class MentorshipApplication(
    @Id val id: String? = null,
    @Indexed
    val userId: String,
    val username: String,
    val userEmail: String,

    var status: ApplicationStatus = ApplicationStatus.PENDING,
    val reason: String? = null,
    var rejectionReason: String? = null,

    val appliedAt: Instant = Instant.now(),
    var reviewedAt: Instant? = null,
    var reviewedBy: String? = null
)