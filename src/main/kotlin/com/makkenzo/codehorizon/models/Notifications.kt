package com.makkenzo.codehorizon.models

import com.makkenzo.codehorizon.dtos.NotificationDTO
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

enum class NotificationType {
    MENTORSHIP_APPLICATION_NEW,
    MENTORSHIP_APPLICATION_APPROVED,
    MENTORSHIP_APPLICATION_REJECTED,
    COURSE_PURCHASED,
    COURSE_COMPLETED,
    NEW_REVIEW_ON_COURSE,
    LESSON_COMPLETED_BY_STUDENT,
}

@Document(collection = "notifications")
data class Notification(
    @Id val id: String? = null,
    val userId: String,
    val type: NotificationType,
    val message: String,
    val link: String? = null,
    val relatedEntityId: String? = null,
    var read: Boolean = false,
    val createdAt: Instant = Instant.now()
)

fun Notification.toDTO(): NotificationDTO {
    return NotificationDTO(
        id = this.id!!,
        userId = this.userId,
        type = this.type,
        message = this.message,
        link = this.link,
        relatedEntityId = this.relatedEntityId,
        read = this.read,
        createdAt = this.createdAt
    )
}