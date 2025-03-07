package com.makkenzo.codehorizon.dtos

data class UpdateProfileDTO(
    val avatarUrl: String? = null,
    val bio: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val location: String? = null,
    val website: String? = null,
)