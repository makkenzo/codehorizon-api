package com.makkenzo.codehorizon.dtos

import com.makkenzo.codehorizon.models.CourseDifficultyLevels
import com.makkenzo.codehorizon.models.FeatureItemData
import com.makkenzo.codehorizon.models.TestimonialData
import java.io.Serializable

data class CourseWithoutContentDTO(
    val id: String,
    val title: String,
    val slug: String,
    val description: String?,
    val imagePreview: String?,
    val videoPreview: String?,
    val authorName: String?,
    val authorUsername: String,
    val lessons: List<LessonWithoutContent>,
    val rating: Double,
    val price: Double,
    val discount: Double,
    val difficulty: CourseDifficultyLevels,
    val category: String?,
    val videoLength: Double?,
    val featuresBadge: String? = null,
    val featuresTitle: String? = null,
    val featuresSubtitle: String? = null,
    val featuresDescription: String? = null,
    val features: List<FeatureItemData> = emptyList(),
    val benefitTitle: String? = null,
    val benefitDescription: String? = null,
    val testimonial: TestimonialData? = null,
) : Serializable

data class LessonWithoutContent(
    val id: String,
    val slug: String,
    val title: String,
    val videoLength: Double? = null,
) : Serializable