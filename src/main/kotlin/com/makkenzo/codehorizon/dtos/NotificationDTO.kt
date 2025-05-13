package com.makkenzo.codehorizon.dtos

import com.makkenzo.codehorizon.models.NotificationType
import java.time.Instant

data class NotificationDTO(
    val id: String,
    val userId: String,
    val type: NotificationType,
    val message: String,
    val link: String?,
    val relatedEntityId: String?,
    val read: Boolean,
    val createdAt: Instant
)