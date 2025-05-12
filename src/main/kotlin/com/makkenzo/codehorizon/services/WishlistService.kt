package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.dtos.CourseDTO
import com.makkenzo.codehorizon.dtos.PagedResponseDTO
import com.makkenzo.codehorizon.models.WishlistItem
import com.makkenzo.codehorizon.repositories.WishlistRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service


@Service
class WishlistService(
    private val wishlistRepository: WishlistRepository,
    private val courseService: CourseService,
    private val authorizationService: AuthorizationService
) {
    @CacheEvict(value = ["wishlist"], key = "@authorizationService.getCurrentUserEntity().id")
    fun addToWishlist(courseId: String) {
        val currentUserId = authorizationService.getCurrentUserEntity().id!!
        if (!wishlistRepository.existsByUserIdAndCourseId(currentUserId, courseId)) {
            wishlistRepository.save(WishlistItem(userId = currentUserId, courseId = courseId))
        }
    }

    @CacheEvict(value = ["wishlist"], key = "@authorizationService.getCurrentUserEntity().id")
    fun removeFromWishlist(courseId: String) {
        val currentUserId = authorizationService.getCurrentUserEntity().id!!
        wishlistRepository.deleteByUserIdAndCourseId(currentUserId, courseId)
    }

    @Cacheable(value = ["wishlist"], key = "@authorizationService.getCurrentUserEntity().id")
    fun getWishlist(pageable: Pageable): PagedResponseDTO<CourseDTO> {
        val currentUserId = authorizationService.getCurrentUserEntity().id!!
        val wishlistItems = wishlistRepository.findByUserId(currentUserId, pageable)
        val courseIds = wishlistItems.content.map { it.courseId }
        val courses = courseService.findByIds(courseIds)

        return PagedResponseDTO(
            content = courses,
            pageNumber = wishlistItems.number + 1,
            pageSize = wishlistItems.size,
            totalElements = wishlistItems.totalElements,
            totalPages = wishlistItems.totalPages,
            isLast = wishlistItems.isLast
        )
    }

    fun isCourseInWishlist(courseId: String): Boolean {
        val currentUserId = authorizationService.getCurrentUserEntity().id!!
        return wishlistRepository.existsByUserIdAndCourseId(currentUserId, courseId)
    }
}
