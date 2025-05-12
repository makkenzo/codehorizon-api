package com.makkenzo.codehorizon.controllers

import com.makkenzo.codehorizon.dtos.MentorshipApplicationRequestDTO
import com.makkenzo.codehorizon.dtos.MessageResponseDTO
import com.makkenzo.codehorizon.services.MentorshipApplicationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/mentorship")
@Tag(name = "Mentorship", description = "Операции, связанные с менторством")
@SecurityRequirement(name = "bearerAuth")
class MentorshipController(
    private val mentorshipApplicationService: MentorshipApplicationService
) {
    @PostMapping("/apply")
    @Operation(summary = "Подать заявку на становление ментором")
    @PreAuthorize("hasAuthority('mentorship_application:apply')")
    fun applyForMentorship(
        @Valid @RequestBody applicationRequestDTO: MentorshipApplicationRequestDTO
    ): ResponseEntity<Any> {
        return try {
            val application = mentorshipApplicationService.applyForMentorship(applicationRequestDTO)
            ResponseEntity.status(HttpStatus.CREATED).body(application)
        } catch (e: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).body(MessageResponseDTO(e.message ?: "Ошибка подачи заявки"))
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(MessageResponseDTO("Внутренняя ошибка сервера: ${e.message}"))
        }
    }

    @GetMapping("/application/my")
    @Operation(summary = "Получить статус своей заявки на менторство")
    @PreAuthorize("hasAuthority('mentorship_application:read:self')")
    fun getMyApplicationStatus(): ResponseEntity<Any> {
        val application = mentorshipApplicationService.getUserApplication()
        return if (application != null) {
            ResponseEntity.ok(application)
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(MessageResponseDTO("Заявка на менторство не найдена."))
        }
    }

    @GetMapping("/application/my/active")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Проверить, есть ли активная заявка на менторство")
    fun hasActiveApplication(): ResponseEntity<Map<String, Boolean>> {
        val hasActive = mentorshipApplicationService.hasActiveApplication()
        return ResponseEntity.ok(mapOf("hasActiveApplication" to hasActive))
    }
}