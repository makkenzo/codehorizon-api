package com.makkenzo.codehorizon.controllers

import com.makkenzo.codehorizon.dtos.AdminMentorshipApplicationUpdateRequestDTO
import com.makkenzo.codehorizon.dtos.MentorshipApplicationDTO
import com.makkenzo.codehorizon.dtos.MessageResponseDTO
import com.makkenzo.codehorizon.dtos.PagedResponseDTO
import com.makkenzo.codehorizon.exceptions.NotFoundException
import com.makkenzo.codehorizon.models.ApplicationStatus
import com.makkenzo.codehorizon.services.MentorshipApplicationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin/mentorship/applications")
@Tag(name = "Admin - Mentorship Applications", description = "Управление заявками на менторство")
@SecurityRequirement(name = "bearerAuth")
class AdminMentorshipController(
    private val mentorshipApplicationService: MentorshipApplicationService,
) {
    @GetMapping
    @Operation(summary = "Получить список всех заявок на менторство")
    @PreAuthorize("hasAuthority('mentorship_application:admin:read:any')")
    fun getAllApplications(
        @RequestParam(required = false) status: ApplicationStatus?,
        @RequestParam(defaultValue = "1") @Parameter(description = "Номер страницы (начиная с 1)") page: Int,
        @RequestParam(defaultValue = "10") @Parameter(description = "Количество элементов на странице") size: Int,
        @RequestParam(defaultValue = "appliedAt,desc") @Parameter(description = "Поле для сортировки (напр., appliedAt_asc, username_desc)") sortBy: String
    ): ResponseEntity<PagedResponseDTO<MentorshipApplicationDTO>> {
        val pageIndex = if (page > 0) page - 1 else 0
        val sortParams = sortBy.split("_")
        val sortDirection = if (sortParams.size > 1 && sortParams[1].equals(
                "asc",
                ignoreCase = true
            )
        ) Sort.Direction.ASC else Sort.Direction.DESC
        val sortProperty = if (sortParams.isNotEmpty()) sortParams[0] else "appliedAt"

        val pageable: Pageable = PageRequest.of(pageIndex, size, Sort.by(sortDirection, sortProperty))
        val applicationsPage = mentorshipApplicationService.getAllApplications(status, pageable)
        return ResponseEntity.ok(applicationsPage)
    }

    @PutMapping("/{applicationId}/approve")
    @Operation(summary = "Одобрить заявку на менторство")
    @PreAuthorize("hasAuthority('mentorship_application:admin:approve')")
    fun approveApplication(
        @PathVariable applicationId: String,
    ): ResponseEntity<Any> {
        return try {
            val approvedApplication = mentorshipApplicationService.approveApplication(applicationId)
            ResponseEntity.ok(approvedApplication)
        } catch (e: NotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(MessageResponseDTO(e.message ?: "Заявка не найдена"))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT)
                .body(MessageResponseDTO(e.message ?: "Невозможно одобрить заявку"))
        }
    }

    @PutMapping("/{applicationId}/reject")
    @Operation(summary = "Отклонить заявку на менторство")
    @PreAuthorize("hasAuthority('mentorship_application:admin:reject')")
    fun rejectApplication(
        @PathVariable applicationId: String,
        @Valid @RequestBody requestBody: AdminMentorshipApplicationUpdateRequestDTO,
    ): ResponseEntity<Any> {
        return try {
            val rejectedApplication =
                mentorshipApplicationService.rejectApplication(applicationId, requestBody.rejectionReason)
            ResponseEntity.ok(rejectedApplication)
        } catch (e: NotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(MessageResponseDTO(e.message ?: "Заявка не найдена"))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT)
                .body(MessageResponseDTO(e.message ?: "Невозможно отклонить заявку"))
        }
    }
}