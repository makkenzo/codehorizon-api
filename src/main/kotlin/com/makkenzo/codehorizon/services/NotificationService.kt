package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.dtos.NotificationDTO
import com.makkenzo.codehorizon.dtos.PagedResponseDTO
import com.makkenzo.codehorizon.exceptions.NotFoundException
import com.makkenzo.codehorizon.models.Notification
import com.makkenzo.codehorizon.models.NotificationType
import com.makkenzo.codehorizon.models.toDTO
import com.makkenzo.codehorizon.repositories.NotificationRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val authorizationService: AuthorizationService
) {
    private val logger = LoggerFactory.getLogger(NotificationService::class.java)

    @Transactional
    fun createNotification(
        userId: String,
        type: NotificationType,
        message: String,
        link: String? = null,
        relatedEntityId: String? = null
    ): Notification {
        val notification = Notification(
            userId = userId,
            type = type,
            message = message,
            link = link,
            relatedEntityId = relatedEntityId
        )

        return notificationRepository.save(notification)
    }

    fun getNotificationsForCurrentUser(pageable: Pageable): PagedResponseDTO<NotificationDTO> {
        val currentUserId = authorizationService.getCurrentUserEntity().id!!
        val notificationPage = notificationRepository.findByUserIdOrderByCreatedAtDesc(currentUserId, pageable)

        val content = notificationPage.content.map { it.toDTO() }

        return PagedResponseDTO(
            content = content,
            pageNumber = notificationPage.number,
            pageSize = notificationPage.size,
            totalElements = notificationPage.totalElements,
            totalPages = notificationPage.totalPages,
            isLast = notificationPage.isLast
        )
    }

    fun getUnreadCountForCurrentUser(): Long {
        val currentUserId = authorizationService.getCurrentUserEntity().id!!
        return notificationRepository.countByUserIdAndReadIsFalse(currentUserId)
    }

    @Transactional
    fun markAsRead(notificationId: String): NotificationDTO {
        val currentUserId = authorizationService.getCurrentUserEntity().id!!
        val notification = notificationRepository.findById(notificationId)
            .orElseThrow { NotFoundException("Уведомление с ID $notificationId не найдено") }

        if (notification.userId != currentUserId) {
            throw org.springframework.security.access.AccessDeniedException("Вы не можете изменить это уведомление")
        }

        if (!notification.read) {
            notification.read = true
            return notificationRepository.save(notification).toDTO()
        }
        return notification.toDTO()
    }

    @Transactional
    fun markAllAsReadForCurrentUser(): Int {
        val currentUserId = authorizationService.getCurrentUserEntity().id!!
        val unreadNotifications = notificationRepository.findByUserIdAndReadIsFalse(currentUserId)
        if (unreadNotifications.isEmpty()) {
            return 0
        }
        unreadNotifications.forEach { it.read = true }
        notificationRepository.saveAll(unreadNotifications)
        return unreadNotifications.size
    }
}