package com.makkenzo.codehorizon.dtos

data class CreateCourseRequestDTO(
    val title: String,
    val description: String,
    val price: Double
)