package com.makkenzo.codehorizon.models

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document(collection = "purchases")
data class Purchase(
    @Id val id: String? = null,
    val userId: String,
    val courseId: String,
    val stripeSessionId: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val amount: Long,
    val currency: String
)
