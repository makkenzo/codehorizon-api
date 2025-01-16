package com.makkenzo.codehorizon.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/users")
class UserController {
    @GetMapping("/me")
    @Operation(summary = "Получение информации о пользователе", security = [SecurityRequirement(name = "bearerAuth")])
    fun hello(): ResponseEntity<String> {
        return ResponseEntity.ok("Hello, authenticated user!")
    }
}