package com.makkenzo.codehorizon.controllers

import com.makkenzo.codehorizon.dtos.UpdatePrivacySettingsRequestDTO
import com.makkenzo.codehorizon.models.PrivacySettings
import com.makkenzo.codehorizon.services.ProfileService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/me/settings/privacy")
@Tag(name = "Account Settings - Privacy", description = "Управление настройками конфиденциальности пользователя")
@SecurityRequirement(name = "bearerAuth")
class AccountSettingsController(private val profileService: ProfileService) {
    @GetMapping
    @Operation(summary = "Получить текущие настройки конфиденциальности")
    @PreAuthorize("isAuthenticated()")
    fun getPrivacySettings(): ResponseEntity<PrivacySettings> {
        val settings = profileService.getCurrentUserAccountSettings().privacySettings
        return ResponseEntity.ok(settings)
    }

    @PutMapping
    @Operation(summary = "Обновить настройки конфиденциальности")
    @PreAuthorize("isAuthenticated()")
    fun updatePrivacySettings(
        @Valid @RequestBody privacySettingsDto: UpdatePrivacySettingsRequestDTO
    ): ResponseEntity<PrivacySettings> {
        val updatedSettings = profileService.updatePrivacySettings(privacySettingsDto)
        return ResponseEntity.ok(updatedSettings)
    }
}