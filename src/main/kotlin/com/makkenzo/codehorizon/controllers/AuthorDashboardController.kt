package com.makkenzo.codehorizon.controllers

import com.makkenzo.codehorizon.dtos.AuthorCourseAnalyticsDTO
import com.makkenzo.codehorizon.dtos.AuthorCourseListItemAnalyticsDTO
import com.makkenzo.codehorizon.dtos.PagedResponseDTO
import com.makkenzo.codehorizon.services.AuthorAnalyticsService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/author/dashboard")
@Tag(name = "Author - Dashboard & Analytics", description = "Аналитика и дашборд для авторов курсов")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ROLE_MENTOR') or hasRole('ROLE_ADMIN')")
class AuthorDashboardController(
    private val authorAnalyticsService: AuthorAnalyticsService
) {
    @GetMapping("/my-courses-analytics")
    @Operation(summary = "Получить список своих курсов с базовой аналитикой")
    fun getMyCoursesWithAnalytics(
        @RequestParam(defaultValue = "1") @Parameter(description = "Номер страницы (начиная с 1)") page: Int,
        @RequestParam(defaultValue = "10") @Parameter(description = "Количество элементов на странице") size: Int,
        @RequestParam(required = false) @Parameter(description = "Поле для сортировки (напр., title_asc, totalEnrolledStudents_desc)") sortBy: String?
    ): ResponseEntity<PagedResponseDTO<AuthorCourseListItemAnalyticsDTO>> {
        val pageIndex = if (page > 0) page - 1 else 0

        val sort: Sort = sortBy?.let {
            val parts = it.split("_")
            val property = parts.getOrNull(0) ?: "createdAt"
            val direction = if (parts.getOrNull(1)?.lowercase() == "desc") Sort.Direction.DESC else Sort.Direction.ASC
            Sort.by(direction, property)
        } ?: Sort.by(Sort.Direction.DESC, "createdAt")

        val pageable: Pageable = PageRequest.of(pageIndex, size, sort)
        val coursesAnalyticsPage = authorAnalyticsService.getAuthorCoursesWithAnalytics(pageable)
        return ResponseEntity.ok(coursesAnalyticsPage)
    }

    @GetMapping("/courses/{courseId}/analytics")
    @Operation(summary = "Получить детальную аналитику по своему курсу")
    @PreAuthorize("@authorizationService.isCourseAuthor(#courseId) or hasRole('ROLE_ADMIN')")
    fun getCourseAnalytics(
        @PathVariable courseId: String
    ): ResponseEntity<AuthorCourseAnalyticsDTO> {
        val analytics = authorAnalyticsService.getCourseAnalyticsForAuthor(courseId)
        return ResponseEntity.ok(analytics)
    }
}