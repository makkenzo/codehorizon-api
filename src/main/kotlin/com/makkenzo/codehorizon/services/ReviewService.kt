package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.dtos.*
import com.makkenzo.codehorizon.exceptions.NotFoundException
import com.makkenzo.codehorizon.models.Review
import com.makkenzo.codehorizon.repositories.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

import java.time.Instant

@Service
class ReviewService(
    private val courseProgressRepository: CourseProgressRepository,
    private val reviewRepository: ReviewRepository,
    private val userRepository: UserRepository,
    private val profileRepository: ProfileRepository,
    private val courseRepository: CourseRepository
) {
    @Transactional
    fun createReview(courseId: String, authorId: String, dto: CreateReviewRequestDTO): ReviewDTO {
        if (!courseProgressRepository.existsByUserIdAndCourseId(
                authorId,
                courseId
            )
        ) {
            throw AccessDeniedException("У вас нет доступа для оставления отзыва на этот курс.")
        }

        if (reviewRepository.existsByAuthorIdAndCourseId(authorId, courseId)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Вы уже оставили отзыв для этого курса.")
        }

        if (dto.rating < 1 || dto.rating > 5) {
            throw IllegalArgumentException("Рейтинг должен быть от 1 до 5.")
        }

        val review = Review(
            courseId = courseId,
            authorId = authorId,
            rating = dto.rating,
            text = dto.text
        )
        val savedReview = reviewRepository.save(review)

        updateCourseAverageRating(courseId)

        return mapReviewToDTO(savedReview)
    }

    fun getReviewsByCourseId(courseId: String, pageable: Pageable): PagedResponseDTO<ReviewDTO> {
        val reviewPage: Page<Review> = reviewRepository.findByCourseId(courseId, pageable)
        val reviewDTOs = reviewPage.content.map { mapReviewToDTO(it) }

        return PagedResponseDTO(
            content = reviewDTOs,
            pageNumber = reviewPage.number,
            pageSize = reviewPage.size,
            totalElements = reviewPage.totalElements,
            totalPages = reviewPage.totalPages,
            isLast = reviewPage.isLast
        )
    }

    @Transactional
    fun updateReview(reviewId: String, userId: String, dto: UpdateReviewRequestDTO): ReviewDTO {
        val review = reviewRepository.findById(reviewId)
            .orElseThrow { NotFoundException("Отзыв с ID $reviewId не найден") }

        if (review.authorId != userId) {
            throw AccessDeniedException("Вы не можете редактировать этот отзыв.")
        }

        if (dto.rating < 1 || dto.rating > 5) {
            throw IllegalArgumentException("Рейтинг должен быть от 1 до 5.")
        }

        review.rating = dto.rating
        review.text = dto.text
        review.updatedAt = Instant.now()

        val updatedReview = reviewRepository.save(review)
        updateCourseAverageRating(review.courseId)
        return mapReviewToDTO(updatedReview)
    }

    @Transactional
    fun deleteReview(reviewId: String, userId: String) {
        val review = reviewRepository.findById(reviewId)
            .orElseThrow { NotFoundException("Отзыв с ID $reviewId не найден") }


        if (review.authorId != userId) {
            throw AccessDeniedException("Вы не можете удалить этот отзыв.")
        }

        val courseId = review.courseId
        reviewRepository.deleteById(reviewId)
        updateCourseAverageRating(courseId)
    }

    fun getReviewByAuthorAndCourse(authorId: String, courseId: String): ReviewDTO {
        val review = reviewRepository.findByAuthorIdAndCourseId(authorId, courseId)
            ?: throw NotFoundException("Отзыв пользователя $authorId для курса $courseId не найден.")
        return mapReviewToDTO(review)
    }

    private fun updateCourseAverageRating(courseId: String) {
        val reviews = reviewRepository.findAllByCourseId(courseId)
        val averageRating = if (reviews.isNotEmpty()) {
            reviews.map { it.rating }.average()
        } else {
            0.0
        }

        val course = courseRepository.findById(courseId).orElse(null)
        course?.let {
            it.rating = averageRating
            courseRepository.save(it)
        }
    }

    private fun mapReviewToDTO(review: Review): ReviewDTO {
        val author = userRepository.findById(review.authorId).orElse(null)
        val profile = author?.let { profileRepository.findByUserId(it.id!!) }

        val authorDTO = ReviewAuthorDTO(
            username = author?.username ?: "Неизвестный",
            avatarUrl = profile?.avatarUrl,
            avatarColor = profile?.avatarColor
        )

        return ReviewDTO(
            id = review.id!!,
            rating = review.rating,
            text = review.text,
            author = authorDTO,
            createdAt = review.createdAt,
            updatedAt = review.updatedAt
        )
    }
}