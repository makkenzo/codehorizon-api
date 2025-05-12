package com.makkenzo.codehorizon.controllers

import com.makkenzo.codehorizon.dtos.AdminChartDataDTO
import com.makkenzo.codehorizon.dtos.AdminDashboardStatsDTO
import com.makkenzo.codehorizon.services.ReportingService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/dashboard")
@Tag(name = "Admin - Dashboard", description = "Данные для дашборда админа")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAuthority('admin_dashboard:view')")
class AdminDashboardController(
    private val reportingService: ReportingService
) {
    @GetMapping("/stats")
    @Operation(summary = "Получить статистику для карточек дашборда")
    fun getDashboardStats(): ResponseEntity<AdminDashboardStatsDTO> {
        val stats = reportingService.getDashboardStats()
        return ResponseEntity.ok(stats)
    }

    @GetMapping("/charts")
    @Operation(summary = "Получить данные для графиков дашборда")
    fun getDashboardCharts(): ResponseEntity<AdminChartDataDTO> {
        val chartData = reportingService.getChartData()
        return ResponseEntity.ok(chartData)
    }
}