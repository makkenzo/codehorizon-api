package com.makkenzo.codehorizon.dtos

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegisterRequestDTO(
    @field:NotBlank(message = "Имя пользователя не может быть пустым")
    @field:Size(min = 3, max = 50, message = "Имя пользователя должно содержать от 3 до 50 символов")
    val username: String,

    @field:NotBlank(message = "Email не может быть пустым")
    @field:Email(message = "Некорректный формат email")
    val email: String,
    @field:NotBlank(message = "Пароль не может быть пустым")
    val password: String,
    @field:NotBlank(message = "Подтверждение пароля не может быть пустым")
    val confirmPassword: String
)