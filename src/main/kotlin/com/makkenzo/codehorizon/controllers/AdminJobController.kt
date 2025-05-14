package com.makkenzo.codehorizon.controllers

import com.makkenzo.codehorizon.services.AchievementService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/jobs")
@Tag(name = "Admin Job Management")
@SecurityRequirement(name = "Bearer")
class AdminJobController(
    private val achievementService: AchievementService
) {
    @PostMapping("/achievements/retroactive-grant")
    @Operation(summary = "Запустить ретроактивную выдачу достижений всем пользователям")
    @PreAuthorize("hasAuthority('admin:job:run')")
    fun runRetroactiveAchievementGrant(
        @Parameter(description = "Опциональный список ключей достижений для проверки (если пусто - проверять все)")
        @RequestBody(required = false) achievementKeys: List<String>?
    ): ResponseEntity<Map<String, String>> {
        achievementService.retroactivelyCheckAndGrantAllAchievementsForAllUsers(achievementKeys)
        return ResponseEntity.ok(mapOf("message" to "Задача ретроактивной выдачи достижений запущена в фоновом режиме."))
    }
}