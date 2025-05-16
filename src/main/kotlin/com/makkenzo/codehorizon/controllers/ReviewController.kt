package com.makkenzo.codehorizon.controllers

import com.makkenzo.codehorizon.configs.KeyStrategy
import com.makkenzo.codehorizon.configs.RateLimited
import com.makkenzo.codehorizon.dtos.*
import com.makkenzo.codehorizon.exceptions.NotFoundException
import com.makkenzo.codehorizon.repositories.CourseRepository
import com.makkenzo.codehorizon.services.CourseService
import com.makkenzo.codehorizon.services.ReviewService
import com.makkenzo.codehorizon.utils.JwtUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/courses/{courseId}/reviews")
@Tag(name = "Reviews", description = "Отзывы к курсам")
class ReviewController(
    private val reviewService: ReviewService,
    private val jwtUtils: JwtUtils,
    private val courseService: CourseService,
    private val courseRepository: CourseRepository
) {
    @GetMapping
    @Operation(summary = "Получить отзывы для курса")
    fun getReviews(
        @PathVariable courseId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
        @RequestParam(defaultValue = "createdAt,desc") sortBy: String
    ): ResponseEntity<PagedResponseDTO<ReviewDTO>> {
        val sortParams = sortBy.split(",")
        val sortDirection = if (sortParams.size > 1 && sortParams[1].equals(
                "asc",
                ignoreCase = true
            )
        ) Sort.Direction.ASC else Sort.Direction.DESC
        val sortProperty = if (sortParams.isNotEmpty()) sortParams[0] else "createdAt"
        val pageable: Pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortProperty))

        val reviewsPage = reviewService.getReviewsByCourseId(courseId, pageable)
        return ResponseEntity.ok(reviewsPage)
    }

    @GetMapping("/distribution")
    @Operation(summary = "Получить распределение оценок для курса")
    fun getRatingDistribution(
        @PathVariable courseId: String
    ): ResponseEntity<List<RatingDistributionDTO>> {
        val distribution = reviewService.getRatingDistribution(courseId)
        return ResponseEntity.ok(distribution)
    }

    @PostMapping
    @Operation(summary = "Создать отзыв для курса", security = [SecurityRequirement(name = "bearerAuth")])
    @PreAuthorize("hasAuthority('review:create')")
    @RateLimited(
        limit = 5,
        durationSeconds = 3600,
        strategy = KeyStrategy.USER_ID,
        keyPrefix = "rl_create_review_user_"
    )
    fun createReview(
        @PathVariable courseId: String,
        @Valid @RequestBody reviewDto: CreateReviewRequestDTO
    ): ResponseEntity<ReviewDTO> {
        val course = courseRepository.findById(courseId)
            .orElseThrow { NotFoundException("Курс $courseId не найден") }

        val createdReview = reviewService.createReview(courseId, reviewDto, course.slug)
        return ResponseEntity.status(HttpStatus.CREATED).body(createdReview)
    }

    @PutMapping("/{reviewId}")
    @Operation(summary = "Обновить свой отзыв", security = [SecurityRequirement(name = "bearerAuth")])
    @PreAuthorize("@authorizationService.canEditOwnReview(#reviewId)")
    fun updateReview(
        @PathVariable courseId: String,
        @PathVariable reviewId: String,
        @Valid @RequestBody reviewDto: UpdateReviewRequestDTO
    ): ResponseEntity<ReviewDTO> {
        val updatedReview = reviewService.updateReview(reviewId, reviewDto)
        return ResponseEntity.ok(updatedReview)
    }

    @DeleteMapping("/{reviewId}")
    @Operation(summary = "Удалить свой отзыв", security = [SecurityRequirement(name = "bearerAuth")])
    @PreAuthorize("@authorizationService.canDeleteOwnReview(#reviewId)")
    fun deleteReview(
        @PathVariable courseId: String,
        @PathVariable reviewId: String
    ): ResponseEntity<Void> {
        reviewService.deleteReview(reviewId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/me")
    @Operation(summary = "Получить свой отзыв для курса", security = [SecurityRequirement(name = "bearerAuth")])
    @PreAuthorize("isAuthenticated()")
    fun getMyReviewForCourse(
        @PathVariable courseId: String
    ): ResponseEntity<ReviewDTO> {
        val review = reviewService.getReviewByAuthorAndCourse(courseId)
        return ResponseEntity.ok(review)
    }
}