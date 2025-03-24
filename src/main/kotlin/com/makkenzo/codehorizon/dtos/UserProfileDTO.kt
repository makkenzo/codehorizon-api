package com.makkenzo.codehorizon.dtos

data class UserProfileDTO(
    val id: String,
    val username: String,
    val email: String,
    val isVerified: Boolean,
    val profile: ProfileDTO
)