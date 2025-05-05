package com.makkenzo.codehorizon.models

import java.io.Serializable

data class StripeCheckoutSession(
    val id: String,
    val amountTotal: Long,
    val currency: String,
    val paymentStatus: String,
    val metadata: Map<String, String>
) : Serializable