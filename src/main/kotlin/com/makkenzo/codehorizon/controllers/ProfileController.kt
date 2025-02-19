package com.makkenzo.codehorizon.controllers

import com.makkenzo.codehorizon.annotations.JwtAuth
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
@RequestMapping("/profiles")
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
    @JwtAuth
    fun getProfile(request: HttpServletRequest): ResponseEntity<Profile> {
        val token = request.getHeader("Authorization")
            ?: throw IllegalArgumentException("Authorization header is missing")
        val userId = jwtUtils.getIdFromToken(token.substring(7).trim())
        val profile = profileService.getProfileByUserId(userId)
        return ResponseEntity.ok(profile)
    }

    @PutMapping("/")
    @Operation(
        summary = "Обновление профиля текущего пользователя",
        security = [SecurityRequirement(name = "bearerAuth")]
    )
    @JwtAuth
    fun updateProfile(
        @RequestBody profile: Profile,
        request: HttpServletRequest
    ): ResponseEntity<Profile> {
        val token = request.getHeader("Authorization")
            ?: throw IllegalArgumentException("Authorization header is missing")
        val userId = jwtUtils.getIdFromToken(token.substring(7).trim())
        val updatedProfile = profileService.updateProfile(userId, profile)
        return ResponseEntity.ok(updatedProfile)
    }

    @DeleteMapping("/")
    @Operation(
        summary = "Удаление профиля текущего пользователя",
        security = [SecurityRequirement(name = "bearerAuth")]
    )
    @JwtAuth
    fun deleteProfile(request: HttpServletRequest): ResponseEntity<Void> {
        val token = request.getHeader("Authorization")
            ?: throw IllegalArgumentException("Authorization header is missing")
        val userId = jwtUtils.getIdFromToken(token.substring(7).trim())
        profileService.deleteProfile(userId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Получение профиля по userId (например, для администратора)")
    fun getProfileByUserId(@PathVariable userId: String): ResponseEntity<Profile> {
        val profile = profileService.getProfileByUserId(userId)
        return ResponseEntity.ok(profile)
    }
}