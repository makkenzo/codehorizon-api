package com.makkenzo.codehorizon.dtos

import jakarta.validation.constraints.NotBlank

data class LoginRequestDTO(
    @field:NotBlank(message = "Логин/Email не может быть пустым")
    val login: String,
    @field:NotBlank(message = "Пароль не может быть пустым")
    val password: String
)