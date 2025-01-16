package com.makkenzo.codehorizon.dtos

data class RegisterRequestDTO(
    val username: String,
    val email: String,
    val password: String
)