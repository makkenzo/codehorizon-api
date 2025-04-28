package com.makkenzo.codehorizon.dtos

data class CategoryDistributionDTO(
    val category: String,
    val courseCount: Int,
    val fill: String? = null
)
