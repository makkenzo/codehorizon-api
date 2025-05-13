package com.makkenzo.codehorizon.controllers

import com.makkenzo.codehorizon.dtos.NotificationDTO
import com.makkenzo.codehorizon.dtos.PagedResponseDTO
import com.makkenzo.codehorizon.services.NotificationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "Управление уведомлениями пользователя")
@SecurityRequirement(name = "bearerAuth")
class NotificationController(private val notificationService: NotificationService) {
    @GetMapping
    @Operation(summary = "Получить уведомления для текущего пользователя")
    @PreAuthorize("isAuthenticated()")
    fun getNotifications(
        @RequestParam(defaultValue = "1") @Parameter(description = "Номер страницы (начиная с 1)") page: Int,
        @RequestParam(defaultValue = "10") @Parameter(description = "Количество элементов на странице") size: Int
    ): ResponseEntity<PagedResponseDTO<NotificationDTO>> {
        val pageIndex = if (page > 0) page - 1 else 0
        val pageable: Pageable = PageRequest.of(pageIndex, size)
        val notifications = notificationService.getNotificationsForCurrentUser(pageable)
        return ResponseEntity.ok(notifications)
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Получить количество непрочитанных уведомлений")
    @PreAuthorize("isAuthenticated()")
    fun getUnreadCount(): ResponseEntity<Map<String, Long>> {
        val count = notificationService.getUnreadCountForCurrentUser()
        return ResponseEntity.ok(mapOf("unreadCount" to count))
    }

    @PostMapping("/{notificationId}/read")
    @Operation(summary = "Пометить уведомление как прочитанное")
    @PreAuthorize("isAuthenticated()")
    fun markNotificationAsRead(@PathVariable notificationId: String): ResponseEntity<NotificationDTO> {
        val notification = notificationService.markAsRead(notificationId)
        return ResponseEntity.ok(notification)
    }

    @PostMapping("/read-all")
    @Operation(summary = "Пометить все уведомления как прочитанные")
    @PreAuthorize("isAuthenticated()")
    fun markAllNotificationsAsRead(): ResponseEntity<Map<String, Int>> {
        val count = notificationService.markAllAsReadForCurrentUser()
        return ResponseEntity.ok(mapOf("markedAsReadCount" to count))
    }
}