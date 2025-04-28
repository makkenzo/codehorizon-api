package com.makkenzo.codehorizon.controllers

import com.makkenzo.codehorizon.dtos.PagedResponseDTO
import com.makkenzo.codehorizon.dtos.UserCourseDTO
import com.makkenzo.codehorizon.dtos.UserProfileDTO
import com.makkenzo.codehorizon.services.CourseProgressService
import com.makkenzo.codehorizon.services.UserService
import com.makkenzo.codehorizon.utils.JwtUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/users")
@Tag(name = "User")
class UserController(
    private val userService: UserService,
    private val jwtUtils: JwtUtils,
    private val courseProgressService: CourseProgressService
) {
    @GetMapping("/{username}/profile")
    @Operation(summary = "Получение профиля по username")
    fun getProfileByUsername(@PathVariable username: String): ResponseEntity<UserProfileDTO> {
        val profile = userService.getProfileByUsername(username)
        return ResponseEntity.ok(profile)
    }

    @GetMapping("/me/courses")
    @Operation(summary = "Получить все курсы пользователя", security = [SecurityRequirement(name = "bearerAuth")])
    fun getAllMyCourses(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
        request: HttpServletRequest
    ): ResponseEntity<PagedResponseDTO<UserCourseDTO>> {
        val token = request.cookies?.find { it.name == "access_token" }?.value
            ?: throw IllegalArgumentException("Access token cookie is missing")
        val userId = jwtUtils.getIdFromToken(token)

        val pageable: Pageable = PageRequest.of(
            page - 1, size, Sort.by(
                Sort.Direction.DESC, "lastUpdated"
            )
        )

        val courses = courseProgressService.getUserCoursesWithProgress(userId, pageable)

        return ResponseEntity.ok(courses)
    }

    @GetMapping("/me/courses/{courseId}/access")
    @Operation(summary = "Проверка на доступ к курсу", security = [SecurityRequirement(name = "bearerAuth")])
    fun isCourseAccessible(@PathVariable courseId: String, request: HttpServletRequest): ResponseEntity<Boolean> {
        val token = request.cookies?.find { it.name == "access_token" }?.value
            ?: throw IllegalArgumentException("Access token cookie is missing")
        val userId = jwtUtils.getIdFromToken(token)
        return ResponseEntity.ok(courseProgressService.getUserCourseProgress(userId, courseId) != null)
    }
}