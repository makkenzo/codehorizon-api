package com.makkenzo.codehorizon.dtos

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant

data class CreateReviewRequestDTO(
    @field:NotNull(message = "Рейтинг не может быть null")
    @field:Min(value = 1, message = "Рейтинг не может быть меньше 1")
    @field:Max(value = 5, message = "Рейтинг не может быть больше 5")
    val rating: Int,

    @field:Size(max = 2000, message = "Текст отзыва не должен превышать 2000 символов")
    val text: String?
)

data class UpdateReviewRequestDTO(
    @field:NotNull(message = "Рейтинг не может быть null")
    @field:Min(value = 1, message = "Рейтинг не может быть меньше 1")
    @field:Max(value = 5, message = "Рейтинг не может быть больше 5")
    val rating: Int,

    @field:Size(max = 2000, message = "Текст отзыва не должен превышать 2000 символов")
    val text: String?
)

data class ReviewAuthorDTO(
    val username: String,
    val avatarUrl: String?,
    val avatarColor: String?
)

data class ReviewDTO(
    val id: String,
    val rating: Int,
    val text: String?,
    val author: ReviewAuthorDTO,
    val createdAt: Instant,
    val updatedAt: Instant
)