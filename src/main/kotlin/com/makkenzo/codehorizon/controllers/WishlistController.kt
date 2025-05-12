package com.makkenzo.codehorizon.controllers

import com.makkenzo.codehorizon.dtos.CourseDTO
import com.makkenzo.codehorizon.dtos.MessageResponseDTO
import com.makkenzo.codehorizon.dtos.PagedResponseDTO
import com.makkenzo.codehorizon.services.WishlistService
import com.makkenzo.codehorizon.utils.JwtUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/users/me/wishlist")
@Tag(name = "Wishlist")
@SecurityRequirement(name = "bearerAuth")
class WishlistController(
    private val wishlistService: WishlistService,
    private val jwtUtils: JwtUtils
) {
    @PostMapping("/{courseId}")
    @Operation(summary = "Добавление курса в желаемое", security = [SecurityRequirement(name = "bearerAuth")])
    @PreAuthorize("hasAuthority('wishlist:add:self')")
    fun addToWishlist(@PathVariable courseId: String): ResponseEntity<MessageResponseDTO> {
        wishlistService.addToWishlist(courseId)
        return ResponseEntity.ok(MessageResponseDTO("Курс добавлен в желаемое"))
    }

    @DeleteMapping("/{courseId}")
    @Operation(summary = "Удаление курса из желаемого", security = [SecurityRequirement(name = "bearerAuth")])
    @PreAuthorize("hasAuthority('wishlist:remove:self')")
    fun removeFromWishlist(
        @PathVariable courseId: String
    ): ResponseEntity<MessageResponseDTO> {
        wishlistService.removeFromWishlist(courseId)
        return ResponseEntity.ok(MessageResponseDTO("Курс удален из желаемого"))
    }

    @GetMapping
    @Operation(summary = "Получение желаемого", security = [SecurityRequirement(name = "bearerAuth")])
    @PreAuthorize("hasAuthority('wishlist:read:self')")
    fun getWishlist(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
    ): PagedResponseDTO<CourseDTO> {
        val pageable: Pageable = PageRequest.of(
            page - 1, size
        )

        return wishlistService.getWishlist(pageable)
    }

    @GetMapping("/status")
    @Operation(summary = "Проверка наличия курса в желаемом", security = [SecurityRequirement(name = "bearerAuth")])
    @PreAuthorize("isAuthenticated()")
    fun isCourseInWishlist(@RequestParam courseId: String): ResponseEntity<Boolean> {
        return ResponseEntity.ok(wishlistService.isCourseInWishlist(courseId))
    }
}
