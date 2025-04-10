package com.makkenzo.codehorizon.controllers

import com.makkenzo.codehorizon.annotations.CookieAuth
import com.makkenzo.codehorizon.dtos.CourseDTO
import com.makkenzo.codehorizon.dtos.MessageResponseDTO
import com.makkenzo.codehorizon.dtos.PagedResponseDTO
import com.makkenzo.codehorizon.services.WishlistService
import com.makkenzo.codehorizon.utils.JwtUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/users/me/wishlist")
@Tag(name = "Wishlist")
class WishlistController(
    private val wishlistService: WishlistService,
    private val jwtUtils: JwtUtils
) {
    @PostMapping("/{courseId}")
    @Operation(summary = "Добавление курса в желаемое", security = [SecurityRequirement(name = "bearerAuth")])
    @CookieAuth
    fun addToWishlist(@PathVariable courseId: String, request: HttpServletRequest): ResponseEntity<MessageResponseDTO> {
        val token = request.cookies?.find { it.name == "access_token" }?.value
            ?: throw IllegalArgumentException("Access token cookie is missing")
        val userId = jwtUtils.getIdFromToken(token)

        wishlistService.addToWishlist(userId, courseId)
        return ResponseEntity.ok(MessageResponseDTO("Курс добавлен в желаемое"))
    }

    @DeleteMapping("/{courseId}")
    @Operation(summary = "Удаление курса из желаемого", security = [SecurityRequirement(name = "bearerAuth")])
    @CookieAuth
    fun removeFromWishlist(
        @PathVariable courseId: String,
        request: HttpServletRequest
    ): ResponseEntity<MessageResponseDTO> {
        val token = request.cookies?.find { it.name == "access_token" }?.value
            ?: throw IllegalArgumentException("Access token cookie is missing")
        val userId = jwtUtils.getIdFromToken(token)
        wishlistService.removeFromWishlist(userId, courseId)

        return ResponseEntity.ok(MessageResponseDTO("Курс удален из желаемого"))
    }

    @GetMapping
    @Operation(summary = "Получение желаемого", security = [SecurityRequirement(name = "bearerAuth")])
    @CookieAuth
    fun getWishlist(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "10") size: Int, request: HttpServletRequest
    ): PagedResponseDTO<CourseDTO> {
        val token = request.cookies?.find { it.name == "access_token" }?.value
            ?: throw IllegalArgumentException("Access token cookie is missing")
        val userId = jwtUtils.getIdFromToken(token)

        val pageable: Pageable = PageRequest.of(
            page - 1, size
        )

        return wishlistService.getWishlist(userId, pageable)
    }

    @GetMapping("/status")
    @Operation(summary = "Проверка наличия курса в желаемом", security = [SecurityRequirement(name = "bearerAuth")])
    @CookieAuth
    fun isCourseInWishlist(@RequestParam courseId: String, request: HttpServletRequest): ResponseEntity<Boolean> {
        val token = request.cookies?.find { it.name == "access_token" }?.value
            ?: throw IllegalArgumentException("Access token cookie is missing")
        val userId = jwtUtils.getIdFromToken(token)
        return ResponseEntity.ok(wishlistService.isCourseInWishlist(userId, courseId))
    }
}
