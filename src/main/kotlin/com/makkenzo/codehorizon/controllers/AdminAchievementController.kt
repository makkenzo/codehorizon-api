package com.makkenzo.codehorizon.controllers

import com.makkenzo.codehorizon.dtos.AdminCreateAchievementDTO
import com.makkenzo.codehorizon.dtos.AdminUpdateAchievementDTO
import com.makkenzo.codehorizon.dtos.PagedResponseDTO
import com.makkenzo.codehorizon.models.Achievement
import com.makkenzo.codehorizon.services.AdminAchievementManagementService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/admin/achievements")
@Tag(name = "Admin - Achievements", description = "Управление определениями достижений")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAuthority('achievement:admin:manage')")
class AdminAchievementController(private val adminAchievementManagementService: AdminAchievementManagementService) {

    @PostMapping
    @Operation(summary = "Создать новое определение достижения")
    fun createAchievement(@Valid @RequestBody achievementDto: AdminCreateAchievementDTO): ResponseEntity<Achievement> {
        return try {
            val createdAchievement = adminAchievementManagementService.createAchievement(achievementDto)
            ResponseEntity.status(HttpStatus.CREATED).body(createdAchievement)
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
        }
    }

    @GetMapping
    @Operation(summary = "Получить все определения достижений (с пагинацией)")
    fun getAllAchievements(
        @PageableDefault(size = 20, sort = ["order"])
        @Parameter(description = "Параметры пагинации и сортировки (например, page=0&size=10&sort=name,asc)")
        pageable: Pageable
    ): ResponseEntity<PagedResponseDTO<Achievement>> {
        val achievementsPage = adminAchievementManagementService.getAllAchievementsPagedDTO(pageable)
        return ResponseEntity.ok(achievementsPage)
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить определение достижения по ID")
    fun getAchievementById(@PathVariable id: String): ResponseEntity<Achievement> {
        val achievement = adminAchievementManagementService.getAchievementById(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Достижение с ID $id не найдено")
        return ResponseEntity.ok(achievement)
    }

    @PutMapping("/{id}")
    @Operation(summary = "Обновить определение достижения")
    fun updateAchievement(
        @PathVariable id: String,
        @Valid @RequestBody achievementDetailsDto: AdminUpdateAchievementDTO
    ): ResponseEntity<Achievement> {
        return try {
            val updatedAchievement = adminAchievementManagementService.updateAchievement(id, achievementDetailsDto)
            ResponseEntity.ok(updatedAchievement)
        } catch (e: NoSuchElementException) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить определение достижения")
    fun deleteAchievement(@PathVariable id: String): ResponseEntity<Void> {
        val deleted = adminAchievementManagementService.deleteAchievement(id)
        return if (deleted) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()
    }

    @GetMapping("/categories")
    @Operation(summary = "Получить список уникальных категорий достижений")
    fun getAchievementCategories(): ResponseEntity<List<String>> {
        val categories = adminAchievementManagementService.getDistinctAchievementCategories()
        return ResponseEntity.ok(categories)
    }
}