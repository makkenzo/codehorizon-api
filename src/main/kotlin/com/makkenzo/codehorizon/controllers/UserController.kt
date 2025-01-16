package com.makkenzo.codehorizon.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/users")
@Tag(name = "User")
class UserController {
    @GetMapping("/me")
    @Operation(summary = "Получение информации о пользователе", security = [SecurityRequirement(name = "bearerAuth")])
    fun hello(): ResponseEntity<String> {
        return try {
            ResponseEntity.ok("Hello, authenticated user!")
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
    }
}