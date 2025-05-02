package com.makkenzo.codehorizon.dtos

import jakarta.validation.constraints.NotBlank

data class ResetPasswordRequestDTO(
    @field:NotBlank(message = "Пароль не может быть пустым")
    val password: String,

    @field:NotBlank(message = "Подтверждение пароля не может быть пустым")
    val confirmPassword: String
)