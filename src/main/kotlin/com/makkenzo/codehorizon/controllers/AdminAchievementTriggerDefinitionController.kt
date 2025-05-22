package com.makkenzo.codehorizon.controllers

import com.makkenzo.codehorizon.dtos.CreateAchievementTriggerDefinitionDTO
import com.makkenzo.codehorizon.dtos.UpdateAchievementTriggerDefinitionDTO
import com.makkenzo.codehorizon.models.AchievementTriggerDefinition
import com.makkenzo.codehorizon.services.AchievementTriggerDefinitionService
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin/achievement-trigger-definitions")
@Tag(name = "Admin - Achievement Trigger Definitions")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAuthority('admin:achievement_trigger_definitions')") // TODO: Define this authority
class AdminAchievementTriggerDefinitionController(
    private val service: AchievementTriggerDefinitionService
) {

    @PostMapping
    fun createDefinition(
        @Valid @RequestBody dto: CreateAchievementTriggerDefinitionDTO
    ): ResponseEntity<AchievementTriggerDefinition> {
        return try {
            val definition = service.create(dto)
            ResponseEntity(definition, HttpStatus.CREATED)
        } catch (e: IllegalArgumentException) {
            // Consider a more specific error handling/logging for duplicate key
            ResponseEntity.badRequest().build() // Or a custom error response
        }
    }

    @GetMapping("/{key}")
    fun getDefinitionByKey(@PathVariable key: String): ResponseEntity<AchievementTriggerDefinition> {
        val definition = service.getByKey(key)
        return if (definition != null) {
            ResponseEntity.ok(definition)
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping
    fun getAllDefinitions(pageable: Pageable): ResponseEntity<Page<AchievementTriggerDefinition>> {
        val page = service.getAll(pageable)
        return ResponseEntity.ok(page)
    }

    @PutMapping("/{key}")
    fun updateDefinition(
        @PathVariable key: String,
        @Valid @RequestBody dto: UpdateAchievementTriggerDefinitionDTO
    ): ResponseEntity<AchievementTriggerDefinition> {
        return try {
            val updatedDefinition = service.update(key, dto)
            ResponseEntity.ok(updatedDefinition)
        } catch (e: NoSuchElementException) {
            ResponseEntity.notFound().build()
        } catch (e: IllegalArgumentException) { 
            // This could be thrown by service if, for example, trying to update key or some other immutable field
            ResponseEntity.badRequest().build()
        }
    }

    @DeleteMapping("/{key}")
    fun deleteDefinition(@PathVariable key: String): ResponseEntity<Void> {
        val deleted = service.delete(key)
        return if (deleted) {
            ResponseEntity.noContent().build()
        } else {
            // This implies it was not found, or deletion failed due to other reasons (e.g. foreign key constraint if implemented)
            ResponseEntity.notFound().build() 
        }
    }

    // Consider adding @ExceptionHandler methods for more centralized error handling
    // e.g.
    // @ExceptionHandler(NoSuchElementException::class)
    // fun handleNotFound(ex: NoSuchElementException): ResponseEntity<ApiErrorResponse> { ... }
    // @ExceptionHandler(IllegalArgumentException::class)
    // fun handleBadRequest(ex: IllegalArgumentException): ResponseEntity<ApiErrorResponse> { ... }
}
