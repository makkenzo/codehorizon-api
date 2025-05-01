package com.makkenzo.codehorizon.dtos

import java.time.Instant

data class CreateReviewRequestDTO(
    val rating: Int,
    val text: String?
)

data class UpdateReviewRequestDTO(
    val rating: Int,
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