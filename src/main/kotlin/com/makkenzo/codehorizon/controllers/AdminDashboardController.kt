package com.makkenzo.codehorizon.controllers

import com.makkenzo.codehorizon.dtos.AdminChartDataDTO
import com.makkenzo.codehorizon.dtos.AdminDashboardStatsDTO
import com.makkenzo.codehorizon.dtos.CategoryDistributionDTO
import com.makkenzo.codehorizon.dtos.CoursePopularityDTO
import com.makkenzo.codehorizon.services.CourseService
import com.makkenzo.codehorizon.services.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/admin/dashboard")
@Tag(name = "Admin - Dashboard", description = "Данные для дашборда админа")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
class AdminDashboardController(
    private val userService: UserService,
    private val courseService: CourseService
) {

    @GetMapping("/stats")
    @Operation(summary = "Получить статистику для карточек дашборда")
    fun getDashboardStats(): ResponseEntity<AdminDashboardStatsDTO> {
        // TODO: Реализовать логику подсчета в сервисах
        val stats = AdminDashboardStatsDTO(
            totalUsers = 1567,
            newUsersToday = 23,
            totalCourses = 42,
            totalRevenue = 12345.67,
            activeSessions = 15
        )
        return ResponseEntity.ok(stats)
    }

    @GetMapping("/charts")
    @Operation(summary = "Получить данные для графиков дашборда")
    fun getDashboardCharts(): ResponseEntity<AdminChartDataDTO> {
        // TODO: Реализовать логику получения данных для графиков
        val chartData = AdminChartDataDTO(
            userRegistrations = listOf(
                com.makkenzo.codehorizon.dtos.TimeSeriesDataPointDTO(LocalDate.now().minusDays(2), 5),
                com.makkenzo.codehorizon.dtos.TimeSeriesDataPointDTO(LocalDate.now().minusDays(1), 12),
                com.makkenzo.codehorizon.dtos.TimeSeriesDataPointDTO(LocalDate.now(), 8)
            ),
            revenueData = listOf(
                com.makkenzo.codehorizon.dtos.TimeSeriesDataPointDTO(LocalDate.now().minusDays(2), 150.50),
                com.makkenzo.codehorizon.dtos.TimeSeriesDataPointDTO(LocalDate.now().minusDays(1), 420.00),
                com.makkenzo.codehorizon.dtos.TimeSeriesDataPointDTO(LocalDate.now(), 310.75)
            ),
            categoryDistribution = listOf(
                CategoryDistributionDTO("Web Dev", 15, "#8884d8"),
                CategoryDistributionDTO("Design", 10, "#82ca9d"),
                CategoryDistributionDTO("Mobile", 8, "#ffc658"),
                CategoryDistributionDTO("Marketing", 5, "#ff8042"),
                CategoryDistributionDTO("Other", 4, "#d0ed57")
            ),
            topCoursesByStudents = listOf(
                CoursePopularityDTO("Intro React", 582),
                CoursePopularityDTO("UI Basics", 410),
                CoursePopularityDTO("Kotlin API", 350),
                CoursePopularityDTO("Figma Pro", 290),
                CoursePopularityDTO("Go Backend", 155)
            )
        )
        return ResponseEntity.ok(chartData)
    }
}