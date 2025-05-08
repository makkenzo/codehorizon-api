package com.makkenzo.codehorizon.controllers

import com.makkenzo.codehorizon.dtos.*
import com.makkenzo.codehorizon.services.CourseService
import com.makkenzo.codehorizon.services.ReviewService
import com.makkenzo.codehorizon.utils.JwtUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/courses/{courseId}/reviews")
@Tag(name = "Reviews", description = "Отзывы к курсам")
class ReviewController(
    private val reviewService: ReviewService,
    private val jwtUtils: JwtUtils,
    private val courseService: CourseService
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
    fun createReview(
        @PathVariable courseId: String,
        @Valid @RequestBody reviewDto: CreateReviewRequestDTO,
        request: HttpServletRequest
    ): ResponseEntity<ReviewDTO> {
        val token = request.cookies?.find { it.name == "access_token" }?.value
            ?: throw IllegalArgumentException("Access token cookie is missing")
        val userId = jwtUtils.getIdFromToken(token)

        val course = courseService.getCourseById(courseId)
        val courseSlug = course.slug

        val createdReview = reviewService.createReview(courseId, userId, reviewDto, courseSlug)
        return ResponseEntity.status(HttpStatus.CREATED).body(createdReview)
    }

    @PutMapping("/{reviewId}")
    @Operation(summary = "Обновить свой отзыв", security = [SecurityRequirement(name = "bearerAuth")])
    fun updateReview(
        @PathVariable courseId: String,
        @PathVariable reviewId: String,
        @Valid @RequestBody reviewDto: UpdateReviewRequestDTO,
        request: HttpServletRequest
    ): ResponseEntity<ReviewDTO> {
        val token = request.cookies?.find { it.name == "access_token" }?.value ?: throw IllegalArgumentException("...")
        val userId = jwtUtils.getIdFromToken(token)

        val updatedReview = reviewService.updateReview(reviewId, userId, reviewDto)
        return ResponseEntity.ok(updatedReview)
    }

    @DeleteMapping("/{reviewId}")
    @Operation(summary = "Удалить свой отзыв", security = [SecurityRequirement(name = "bearerAuth")])
    fun deleteReview(
        @PathVariable courseId: String,
        @PathVariable reviewId: String,
        request: HttpServletRequest
    ): ResponseEntity<Void> {
        val token = request.cookies?.find { it.name == "access_token" }?.value ?: throw IllegalArgumentException("...")
        val userId = jwtUtils.getIdFromToken(token)

        reviewService.deleteReview(reviewId, userId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/me")
    @Operation(summary = "Получить свой отзыв для курса", security = [SecurityRequirement(name = "bearerAuth")])
    fun getMyReviewForCourse(
        @PathVariable courseId: String,
        request: HttpServletRequest
    ): ResponseEntity<ReviewDTO> {
        val token = request.cookies?.find { it.name == "access_token" }?.value ?: throw IllegalArgumentException("...")
        val userId = jwtUtils.getIdFromToken(token)
        val review = reviewService.getReviewByAuthorAndCourse(userId, courseId)
        return ResponseEntity.ok(review)
    }
}