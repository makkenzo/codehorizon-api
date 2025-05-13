package com.makkenzo.codehorizon.controllers

import com.makkenzo.codehorizon.dtos.UpdateNotificationPreferencesRequestDTO
import com.makkenzo.codehorizon.models.NotificationPreferences
import com.makkenzo.codehorizon.services.ProfileService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/me/settings/notifications")
@Tag(name = "Account Settings - Notifications", description = "Управление настройками уведомлений пользователя")
@SecurityRequirement(name = "bearerAuth")
class NotificationSettingsController(private val profileService: ProfileService) {
    @GetMapping
    @Operation(summary = "Получить текущие настройки уведомлений")
    @PreAuthorize("isAuthenticated()")
    fun getNotificationPreferences(): ResponseEntity<NotificationPreferences> {
        val settings = profileService.getCurrentUserAccountSettings().notificationPreferences
        return ResponseEntity.ok(settings)
    }

    @PutMapping
    @Operation(summary = "Обновить настройки уведомлений")
    @PreAuthorize("isAuthenticated()")
    fun updateNotificationPreferences(
        @Valid @RequestBody preferencesDto: UpdateNotificationPreferencesRequestDTO
    ): ResponseEntity<NotificationPreferences> {
        val updatedPreferences = profileService.updateNotificationPreferences(preferencesDto)
        return ResponseEntity.ok(updatedPreferences)
    }
}