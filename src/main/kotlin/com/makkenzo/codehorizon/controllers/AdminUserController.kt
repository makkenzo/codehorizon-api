package com.makkenzo.codehorizon.controllers

import com.makkenzo.codehorizon.dtos.AdminUpdateUserRequestDTO
import com.makkenzo.codehorizon.dtos.AdminUserDTO
import com.makkenzo.codehorizon.dtos.PagedResponseDTO
import com.makkenzo.codehorizon.services.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin/users")
@Tag(name = "Admin - Users", description = "Управление пользователями (только для админов)")
@SecurityRequirement(name = "bearerAuth")
class AdminUserController(private val userService: UserService) {
    @GetMapping
    @Operation(summary = "Получить список пользователей (Admin)")
    @PreAuthorize("hasAuthority('user:admin:read:any')")
    fun getAllUsers(
        @RequestParam(defaultValue = "1") @Parameter(description = "Номер страницы (начиная с 1)") page: Int,
        @RequestParam(defaultValue = "20") @Parameter(description = "Количество элементов на странице") size: Int
    ): ResponseEntity<PagedResponseDTO<AdminUserDTO>> {
        val pageIndex = if (page > 0) page - 1 else 0
        val pageable: Pageable = PageRequest.of(pageIndex, size)

        val userPage = userService.findAllUsersAdmin(pageable)
        return ResponseEntity.ok(userPage)
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить пользователя по ID (Admin)")
    @PreAuthorize("hasAuthority('user:admin:read:any')")
    fun getUserById(@PathVariable id: String): ResponseEntity<AdminUserDTO> {
        val userDTO = userService.getUserByIdForAdmin(id)
        return ResponseEntity.ok(userDTO)
    }

    @PutMapping("/{id}")
    @Operation(summary = "Обновить пользователя (Admin)")
    @PreAuthorize("hasAuthority('user:admin:edit:any') and hasAuthority('user:admin:edit_roles')")
    fun updateUser(
        @PathVariable id: String,
        @Valid @RequestBody request: AdminUpdateUserRequestDTO
    ): ResponseEntity<AdminUserDTO> {
        val updatedUserDTO = userService.adminUpdateUser(id, request)
        return ResponseEntity.ok(updatedUserDTO)
    }
}