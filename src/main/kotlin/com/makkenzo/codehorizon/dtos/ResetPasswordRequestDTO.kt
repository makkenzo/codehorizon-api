package com.makkenzo.codehorizon.dtos

data class ResetPasswordRequestDTO(
    val password: String,
    val confirmPassword: String
)