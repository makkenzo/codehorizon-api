package com.makkenzo.codehorizon.dtos

import jakarta.validation.constraints.NotBlank
import java.io.Serializable

data class CheckoutRequestDTO(
    @field:NotBlank(message = "ID курса не может быть пустым")
    val courseId: String,

    @field:NotBlank(message = "ID пользователя не может быть пустым")
    val userId: String,

    val coupon: String? = null
) : Serializable