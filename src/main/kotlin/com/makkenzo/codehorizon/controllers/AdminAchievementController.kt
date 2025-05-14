package com.makkenzo.codehorizon.controllers

import com.makkenzo.codehorizon.models.Achievement
import com.makkenzo.codehorizon.services.AdminAchievementManagementService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin/achievements")
@Tag(name = "Admin - Achievements", description = "Управление определениями достижений")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAuthority('achievement:admin:manage')")
class AdminAchievementController(private val adminAchievementManagementService: AdminAchievementManagementService) {
    @GetMapping
    @Operation(summary = "Получить все определения достижений")
    fun getAllAchievements(): ResponseEntity<List<Achievement>> {
        return ResponseEntity.ok(adminAchievementManagementService.getAllAchievements())
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить определение достижения по ID")
    fun getAchievementById(@PathVariable id: String): ResponseEntity<Achievement> {
        val achievement = adminAchievementManagementService.getAchievementById(id)
        return achievement?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()
    }

    @PostMapping
    @Operation(summary = "Создать новое определение достижения")
    fun createAchievement(@Valid @RequestBody achievement: Achievement): ResponseEntity<Achievement> {
        val createdAchievement = adminAchievementManagementService.createAchievement(achievement)
        return ResponseEntity.status(HttpStatus.CREATED).body(createdAchievement)
    }

    @PutMapping("/{id}")
    @Operation(summary = "Обновить определение достижения")
    fun updateAchievement(
        @PathVariable id: String,
        @Valid @RequestBody achievementDetails: Achievement
    ): ResponseEntity<Achievement> {
        val updatedAchievement = adminAchievementManagementService.updateAchievement(id, achievementDetails)
        return updatedAchievement?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить определение достижения")
    fun deleteAchievement(@PathVariable id: String): ResponseEntity<Void> {
        val deleted = adminAchievementManagementService.deleteAchievement(id)
        return if (deleted) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()
    }
}