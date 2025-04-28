package com.makkenzo.codehorizon.controllers

import com.makkenzo.codehorizon.models.User
import com.makkenzo.codehorizon.services.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin/users")
@Tag(name = "Admin - Users", description = "Управление пользователями (только для админов)")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
class AdminUserController(private val userService: UserService) {
    @GetMapping
    @Operation(summary = "Получить список пользователей (Admin)")
    fun getAllUsers(@PageableDefault(size = 20, page = 0) pageable: Pageable): ResponseEntity<Page<User>> {
        println("Admin requested all users with pageable: $pageable")
        return ResponseEntity.ok(Page.empty(pageable))
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить пользователя по ID (Admin)")
    fun getUserById(@PathVariable id: String): ResponseEntity<User> {
        val user = userService.getUserById(id)
        return ResponseEntity.ok(user)
    }

    // TODO: Добавить DTO для обновления пользователя админом
    data class AdminUpdateUserRequest(
        val roles: List<String>?,
        val isVerified: Boolean?
        // Другие поля, которые может менять админ
    )

    @PutMapping("/{id}")
    @Operation(summary = "Обновить пользователя (Admin)")
    fun updateUser(@PathVariable id: String, @RequestBody request: AdminUpdateUserRequest): ResponseEntity<User> {
        println("Admin trying to update user $id with data: $request")
        val user = userService.getUserById(id)
        return ResponseEntity.ok(user)
    }
}