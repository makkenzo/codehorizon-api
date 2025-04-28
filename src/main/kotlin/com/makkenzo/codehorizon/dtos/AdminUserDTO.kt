package com.makkenzo.codehorizon.dtos

data class AdminUserDTO(
    val id: String,
    val username: String,
    val email: String,
    val isVerified: Boolean,
    val roles: List<String>
)

data class AdminUpdateUserRequestDTO(
    val roles: List<String>? = null,
    val isVerified: Boolean? = null,
)