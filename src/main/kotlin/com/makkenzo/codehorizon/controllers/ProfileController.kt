package com.makkenzo.codehorizon.controllers

import com.makkenzo.codehorizon.dtos.UpdateProfileDTO
import com.makkenzo.codehorizon.models.Profile
import com.makkenzo.codehorizon.services.ProfileService
import com.makkenzo.codehorizon.utils.JwtUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/profiles")
@Tag(name = "Profile")
@SecurityRequirement(name = "bearerAuth")
class ProfileController(
    private val profileService: ProfileService,
    private val jwtUtils: JwtUtils
) {
    @GetMapping("/")
    @Operation(
        summary = "Получение профиля текущего пользователя"
    )
    @PreAuthorize("hasAuthority('user:read:self')")
    fun getProfile(): ResponseEntity<Profile> {
        val profile = profileService.getCurrentUserProfile()
        return ResponseEntity.ok(profile)
    }

    @PutMapping("/")
    @Operation(
        summary = "Обновление профиля текущего пользователя"
    )
    @PreAuthorize("hasAuthority('user:edit:self')")
    fun updateProfile(
        @Valid @RequestBody profileDto: UpdateProfileDTO
    ): ResponseEntity<Profile> {
        val updatedProfile = profileService.updateProfile(profileDto)
        return ResponseEntity.ok(updatedProfile)
    }

    @DeleteMapping("/")
    @Operation(
        summary = "Удаление профиля текущего пользователя"
    )
    @PreAuthorize("hasAuthority('user:delete:self')")
    fun deleteProfile(): ResponseEntity<Void> {
        profileService.deleteProfile()
        return ResponseEntity.noContent().build()
    }
}