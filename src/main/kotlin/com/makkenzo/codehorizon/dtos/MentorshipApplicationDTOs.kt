package com.makkenzo.codehorizon.dtos

import com.makkenzo.codehorizon.models.ApplicationStatus
import jakarta.validation.constraints.Size
import java.time.Instant

data class MentorshipApplicationRequestDTO(
    @field:Size(max = 1000, message = "Причина не должна превышать 1000 символов")
    val reason: String? = null
)

data class AdminMentorshipApplicationUpdateRequestDTO(
    val rejectionReason: String? = null
)

data class MentorshipApplicationDTO(
    val id: String,
    val userId: String,
    val username: String,
    val userEmail: String,
    val status: ApplicationStatus,
    val reason: String?,
    val rejectionReason: String?,
    val appliedAt: Instant,
    val reviewedAt: Instant?,
    val reviewedBy: String?,
    val userRegisteredAt: Instant?
)