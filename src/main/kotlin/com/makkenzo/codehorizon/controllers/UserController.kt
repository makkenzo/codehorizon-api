package com.makkenzo.codehorizon.controllers

import com.makkenzo.codehorizon.dtos.UserProfileDTO
import com.makkenzo.codehorizon.services.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/users")
@Tag(name = "User")
class UserController(
    private val userService: UserService
) {
    @GetMapping("/{username}/profile")
    @Operation(summary = "Получение профиля по username")
    fun getProfileByUsername(@PathVariable username: String): ResponseEntity<UserProfileDTO> {
        val profile = userService.getProfileByUsername(username)
        return ResponseEntity.ok(profile)
    }
}