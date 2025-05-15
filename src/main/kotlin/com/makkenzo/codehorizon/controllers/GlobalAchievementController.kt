package com.makkenzo.codehorizon.controllers

import com.makkenzo.codehorizon.dtos.GlobalAchievementDTO
import com.makkenzo.codehorizon.dtos.PagedResponseDTO
import com.makkenzo.codehorizon.services.GlobalAchievementService
import com.makkenzo.codehorizon.services.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/achievements")
@Tag(name = "Achievements", description = "Операции с достижениями")
class GlobalAchievementController(
    private val userService: UserService,
    private val achievementService: GlobalAchievementService
) {
    private fun parseSortParameter(sortBy: String?): Sort {
        if (sortBy.isNullOrBlank()) return Sort.by(Sort.Direction.ASC, "order")
        val parts = sortBy.split("_")
        val property = parts.getOrNull(0) ?: "order"
        val direction = if (parts.getOrNull(1)?.lowercase() == "desc") Sort.Direction.DESC else Sort.Direction.ASC
        return Sort.by(direction, property)
    }

    @GetMapping("/all")
    @Operation(summary = "Получить список всех определений достижений")
    fun getAllAchievementDefinitions(
        @RequestParam(defaultValue = "1") @Parameter(description = "Номер страницы (начиная с 1)") page: Int,
        @RequestParam(defaultValue = "12") @Parameter(description = "Количество элементов на странице") size: Int,
        @RequestParam(required = false) @Parameter(description = "Поле для сортировки (напр., order_asc, name_desc)") sortBy: String?,
        @RequestParam(required = false) @Parameter(description = "Фильтр по статусу: all, earned, unearned") status: String?,
        @RequestParam(required = false) @Parameter(description = "Фильтр по категории") category: String?,
        @RequestParam(required = false) @Parameter(description = "Поисковый запрос по названию/описанию") q: String?,
        authentication: Authentication?
    ): ResponseEntity<PagedResponseDTO<GlobalAchievementDTO>> {
        val pageIndex = if (page > 0) page - 1 else 0
        val sort = parseSortParameter(sortBy)
        val pageable: Pageable = PageRequest.of(pageIndex, size, sort)

        val userId: String? =
            if (authentication != null && authentication.isAuthenticated && authentication.principal is UserDetails) {
                val userDetails = authentication.principal as UserDetails
                userService.findByEmail(userDetails.username)?.id
            } else if (authentication != null && authentication.isAuthenticated && authentication.principal is String) {
                val principalStr = authentication.principal as String
                userService.findById(principalStr)?.id ?: userService.findByUsername(principalStr)?.id
            } else {
                null
            }

        val achievementsPage = achievementService.getAllDefinitions(pageable, userId, status, category, q)
        return ResponseEntity.ok(achievementsPage)
    }
}