package com.makkenzo.codehorizon.dtos

import jakarta.validation.constraints.NotBlank

data class CheckLoginRequestDTO(
    @field:NotBlank(message = "Логин не может быть пустым")
    val login: String
)