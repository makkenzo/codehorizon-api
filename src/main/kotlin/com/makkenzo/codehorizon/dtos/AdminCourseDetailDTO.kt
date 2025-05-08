package com.makkenzo.codehorizon.dtos

import com.makkenzo.codehorizon.models.CourseDifficultyLevels
import com.makkenzo.codehorizon.models.FeatureItemData
import com.makkenzo.codehorizon.models.Lesson
import com.makkenzo.codehorizon.models.TestimonialData

data class AdminCourseDetailDTO(
    val id: String,
    val title: String,
    val slug: String,
    val description: String?,
    val imagePreview: String?,
    val videoPreview: String?,
    val authorId: String,
    val authorUsername: String,
    val price: Double,
    val discount: Double,
    val isFree: Boolean = false,
    val difficulty: CourseDifficultyLevels,
    val category: String?,
    val videoLength: Double?,
    val lessons: List<Lesson>,
    val featuresBadge: String?,
    val featuresTitle: String?,
    val featuresSubtitle: String?,
    val featuresDescription: String?,
    val features: List<FeatureItemData>?,
    val benefitTitle: String?,
    val benefitDescription: String?,
    val testimonial: TestimonialData?
)
