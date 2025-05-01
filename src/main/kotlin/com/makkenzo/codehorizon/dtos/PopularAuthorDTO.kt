package com.makkenzo.codehorizon.dtos

data class PopularAuthorDTO(
    val userId: String,
    val username: String,
    val firstName: String?,
    val lastName: String?,
    val avatarUrl: String?,
    val avatarColor: String?,
    val courseCount: Int
)