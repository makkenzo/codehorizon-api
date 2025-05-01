package com.makkenzo.codehorizon.dtos

import com.makkenzo.codehorizon.models.CourseDifficultyLevels
import com.makkenzo.codehorizon.models.FeatureItemData
import com.makkenzo.codehorizon.models.TestimonialData

data class AdminCreateUpdateCourseRequestDTO(
    val title: String,
    val description: String?,
    val price: Double,
    val discount: Double? = 0.0,
    val difficulty: CourseDifficultyLevels,
    val category: String?,
    val authorId: String,
    val imagePreview: String? = null,
    val videoPreview: String? = null,

    val featuresBadge: String? = null,
    val featuresTitle: String? = null,
    val featuresSubtitle: String? = null,
    val featuresDescription: String? = null,
    val features: List<FeatureItemData>? = null,
    val benefitTitle: String? = null,
    val benefitDescription: String? = null,
    val testimonial: TestimonialData? = null
)
