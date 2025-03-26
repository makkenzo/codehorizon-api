package com.makkenzo.codehorizon.models

data class StripeCheckoutSession(
    val id: String,
    val amountTotal: Long,
    val currency: String,
    val paymentStatus: String,
    val metadata: Map<String, String>
)