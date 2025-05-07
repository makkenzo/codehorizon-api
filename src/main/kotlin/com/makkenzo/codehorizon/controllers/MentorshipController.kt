package com.makkenzo.codehorizon.controllers

import com.makkenzo.codehorizon.dtos.MentorshipApplicationRequestDTO
import com.makkenzo.codehorizon.dtos.MessageResponseDTO
import com.makkenzo.codehorizon.services.MentorshipApplicationService
import com.makkenzo.codehorizon.utils.JwtUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
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
    private val jwtUtils: JwtUtils,
    private val mentorshipApplicationService: MentorshipApplicationService
) {
    @PostMapping("/apply")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Подать заявку на становление ментором")
    fun applyForMentorship(
        request: HttpServletRequest,
        @Valid @RequestBody applicationRequestDTO: MentorshipApplicationRequestDTO
    ): ResponseEntity<Any> {
        val token = request.cookies?.find { it.name == "access_token" }?.value
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(MessageResponseDTO("Требуется авторизация"))
        val userId = jwtUtils.getIdFromToken(token)

        return try {
            val application = mentorshipApplicationService.applyForMentorship(userId, applicationRequestDTO)
            ResponseEntity.status(HttpStatus.CREATED).body(application)
        } catch (e: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).body(MessageResponseDTO(e.message ?: "Ошибка подачи заявки"))
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(MessageResponseDTO("Внутренняя ошибка сервера: ${e.message}"))
        }
    }

    @GetMapping("/application/my")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Получить статус своей заявки на менторство")
    fun getMyApplicationStatus(request: HttpServletRequest): ResponseEntity<Any> {
        val token = request.cookies?.find { it.name == "access_token" }?.value
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(MessageResponseDTO("Требуется авторизация"))
        val userId = jwtUtils.getIdFromToken(token)

        val application = mentorshipApplicationService.getUserApplication(userId)
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
    fun hasActiveApplication(request: HttpServletRequest): ResponseEntity<Map<String, Boolean>> {
        val token = request.cookies?.find { it.name == "access_token" }?.value
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("hasActiveApplication" to false))
        val userId = jwtUtils.getIdFromToken(token)
        val hasActive = mentorshipApplicationService.hasActiveApplication(userId)
        return ResponseEntity.ok(mapOf("hasActiveApplication" to hasActive))
    }
}