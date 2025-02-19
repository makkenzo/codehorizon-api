package com.makkenzo.codehorizon.models

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "notifications")
data class Notification(
    @Id val id: String? = null,
    val userId: String,
    val message: String,
    val read: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
