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

//    @GetMapping("/me/courses")
//    @Operation(summary = "Получить все курсы пользователя", security = [SecurityRequirement(name = "bearerAuth")])
//    @CookieAuth
//    fun getAllMyCourses(
//        @RequestParam(required = false) title: String?,
//        @RequestParam(required = false) description: String?,
//        @RequestParam(required = false) sortBy: String?,
//        @RequestParam(defaultValue = "0") page: Int,
//        @RequestParam(defaultValue = "10") size: Int
//    ): ResponseEntity<PagedResponseDTO<CourseDTO>> {
//        val pageable: Pageable = PageRequest.of(page, size)
//
//        val courses = courseService.getCourses(
//            title, description, minRating, minDuration, maxDuration, category, difficulty, sortBy, pageable
//        )
//
//        return ResponseEntity.ok(courses)
//    }
}