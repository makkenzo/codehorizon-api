package com.makkenzo.codehorizon.dtos

data class CheckoutRequestDTO(
    val courseId: String,
    val userId: String,
    val coupon: String? = null
)