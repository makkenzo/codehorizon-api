package com.makkenzo.codehorizon.dtos

import jakarta.validation.constraints.Size

data class UpdateProfileDTO(
    val avatarUrl: String? = null,

    @field:Size(max = 500, message = "Био не должно превышать 500 символов")
    val bio: String? = null,

    @field:Size(max = 50, message = "Имя не должно превышать 50 символов")
    val firstName: String? = null,

    @field:Size(max = 50, message = "Фамилия не должна превышать 50 символов")
    val lastName: String? = null,

    @field:Size(max = 100, message = "Местоположение не должно превышать 100 символов")
    val location: String? = null,

    @field:Size(max = 255, message = "URL веб-сайта не должен превышать 255 символов")
    val website: String? = null,
)