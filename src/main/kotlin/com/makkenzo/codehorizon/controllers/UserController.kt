package com.makkenzo.codehorizon.controllers

import com.makkenzo.codehorizon.dtos.*
import com.makkenzo.codehorizon.exceptions.NotFoundException
import com.makkenzo.codehorizon.services.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/users")
@Tag(name = "User")
class UserController(
    private val userService: UserService,
    private val courseProgressService: CourseProgressService,
    private val courseService: CourseService,
    private val authorizationService: AuthorizationService,
    private val certificateService: CertificateService
) {
    @GetMapping("/{username}/certificates/public")
    @Operation(summary = "Получение публичных сертификатов пользователя по username")
    fun getPublicCertificatesByUsername(@PathVariable username: String): ResponseEntity<List<PublicCertificateInfoDTO>> {
        return try {
            val certificates = certificateService.getPublicCertificatesByUsername(username)
            ResponseEntity.ok(certificates)
        } catch (e: NotFoundException) {
            ResponseEntity.notFound().build()
        } catch (e: Exception) {
            // Логирование ошибки
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @GetMapping("/{username}/profile")
    @Operation(summary = "Получение профиля по username")
    fun getProfileByUsername(@PathVariable username: String): ResponseEntity<UserProfileDTO> {
        val profile = userService.getProfileByUsername(username)
        return ResponseEntity.ok(profile)
    }

    @GetMapping("/me/courses")
    @Operation(summary = "Получить все курсы пользователя", security = [SecurityRequirement(name = "bearerAuth")])
    @PreAuthorize("hasAuthority('course:read:list:self_enrolled')")
    fun getAllMyCourses(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ResponseEntity<PagedResponseDTO<UserCourseDTO>> {
        val currentUserId = authorizationService.getCurrentUserEntity().id!!

        val pageable: Pageable = PageRequest.of(
            page - 1, size, Sort.by(
                Sort.Direction.DESC, "lastUpdated"
            )
        )

        val courses = courseProgressService.getUserCoursesWithProgress(currentUserId, pageable)

        return ResponseEntity.ok(courses)
    }

    @GetMapping("/me/courses/{courseId}/access")
    @Operation(summary = "Проверка на доступ к курсу", security = [SecurityRequirement(name = "bearerAuth")])
    @PreAuthorize("isAuthenticated()")
    fun isCourseAccessible(@PathVariable courseId: String): ResponseEntity<Boolean> {
        val currentUserId = authorizationService.getCurrentUserEntity().id!!

        return try {
            val hasAccess = courseService.checkUserAccessToCourse(courseId, currentUserId)
            ResponseEntity.ok(hasAccess)
        } catch (e: NotFoundException) {
            ResponseEntity.notFound().build()
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @GetMapping("/popular-authors")
    @Operation(summary = "Получить список популярных авторов")
    fun getPopularAuthors(
        @RequestParam(defaultValue = "5") limit: Int
    ): ResponseEntity<List<PopularAuthorDTO>> {
        val authors = userService.getPopularAuthors(limit.coerceAtMost(limit))
        return ResponseEntity.ok(authors)
    }
}