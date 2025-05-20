package com.makkenzo.codehorizon.dtos

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class AdminUserDTO(
    val id: String,
    val username: String,
    val email: String,
    val isVerified: Boolean,
    val roles: List<String>,
    val imageUrl: String? = null,
    val createdAt: Instant,
    val lastLoginDate: Instant? = null
)

data class AdminUpdateUserRequestDTO(
    @field:Size(min = 1, message = "Должна быть указана хотя бы одна роль")
    val roles: List<@NotBlank(message = "Роль не может быть пустой") String>? = null,

    val isVerified: Boolean? = null,
)