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
    private val courseService: CourseService
) {
    @CacheEvict(value = ["wishlist"], key = "#userId")
    fun addToWishlist(userId: String, courseId: String) {
        if (!wishlistRepository.existsByUserIdAndCourseId(userId, courseId)) {
            wishlistRepository.save(WishlistItem(userId = userId, courseId = courseId))
        }
    }

    @CacheEvict(value = ["wishlist"], key = "#userId")
    fun removeFromWishlist(userId: String, courseId: String) {
        wishlistRepository.deleteByUserIdAndCourseId(userId, courseId)
    }

    @Cacheable(value = ["wishlist"], key = "#userId")
    fun getWishlist(userId: String, pageable: Pageable): PagedResponseDTO<CourseDTO> {
        val wishlistItems = wishlistRepository.findByUserId(userId, pageable)
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
}
