package com.makkenzo.codehorizon.dtos

data class RatingDistributionDTO(
    val rating: Int,
    val count: Long,
    val percentage: Double
)

data class ReviewsWithDistributionDTO(
    val reviewsPage: PagedResponseDTO<ReviewDTO>,
    val ratingDistribution: List<RatingDistributionDTO> //
)