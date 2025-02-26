package com.makkenzo.codehorizon.controllers

import com.makkenzo.codehorizon.annotations.CookieAuth
import com.makkenzo.codehorizon.models.Profile
import com.makkenzo.codehorizon.services.ProfileService
import com.makkenzo.codehorizon.utils.JwtUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/profiles")
@Tag(name = "Profile")
class ProfileController(
    private val profileService: ProfileService,
    private val jwtUtils: JwtUtils
) {
    @GetMapping("/")
    @Operation(
        summary = "Получение профиля текущего пользователя",
        security = [SecurityRequirement(name = "bearerAuth")]
    )
    @CookieAuth
    fun getProfile(request: HttpServletRequest): ResponseEntity<Profile> {
        val token = request.cookies?.find { it.name == "access_token" }?.value
            ?: throw IllegalArgumentException("Access token cookie is missing")
        val userId = jwtUtils.getIdFromToken(token)
        val profile = profileService.getProfileByUserId(userId)
        return ResponseEntity.ok(profile)
    }

    @PutMapping("/")
    @Operation(
        summary = "Обновление профиля текущего пользователя",
        security = [SecurityRequirement(name = "bearerAuth")]
    )
    @CookieAuth
    fun updateProfile(
        @RequestBody profile: Profile,
        request: HttpServletRequest
    ): ResponseEntity<Profile> {
        val token = request.cookies?.find { it.name == "access_token" }?.value
            ?: throw IllegalArgumentException("Access token cookie is missing")
        val userId = jwtUtils.getIdFromToken(token)
        val updatedProfile = profileService.updateProfile(userId, profile)
        return ResponseEntity.ok(updatedProfile)
    }

    @DeleteMapping("/")
    @Operation(
        summary = "Удаление профиля текущего пользователя",
        security = [SecurityRequirement(name = "bearerAuth")]
    )
    @CookieAuth
    fun deleteProfile(request: HttpServletRequest): ResponseEntity<Void> {
        val token = request.cookies?.find { it.name == "access_token" }?.value
            ?: throw IllegalArgumentException("Access token cookie is missing")
        val userId = jwtUtils.getIdFromToken(token)
        profileService.deleteProfile(userId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Получение профиля по userId")
    fun getProfileByUserId(@PathVariable userId: String): ResponseEntity<Profile> {
        val profile = profileService.getProfileByUserId(userId)
        return ResponseEntity.ok(profile)
    }
}