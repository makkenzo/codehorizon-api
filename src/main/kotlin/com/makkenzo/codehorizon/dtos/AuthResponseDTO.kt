package com.makkenzo.codehorizon.dtos

data class AuthResponseDTO(
    val accessToken: String,
    val refreshToken: String
)