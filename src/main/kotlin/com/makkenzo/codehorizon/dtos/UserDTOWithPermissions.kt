package com.makkenzo.codehorizon.dtos

import com.makkenzo.codehorizon.models.AccountSettings
import java.time.Instant

data class UserDTOWithPermissions(
    val id: String,
    val isVerified: Boolean,
    val username: String,
    val email: String,
    val roles: List<String>,
    val createdCourseIds: List<String>,
    val wishlistId: String?,
    val accountSettings: AccountSettings?,
    val createdAt: Instant,
    val permissions: List<String>
)