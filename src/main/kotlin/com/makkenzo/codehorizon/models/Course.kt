package com.makkenzo.codehorizon.models

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "courses")
data class Course(
    @Id val id: String? = null,
    var title: String,
    var slug: String,
    var description: String? = null,
    val imagePreview: String? = null,
    val videoPreview: String? = null,
    val authorId: String,
    var lessons: MutableList<Lesson> = mutableListOf(),
    var rating: Double = 0.0,
    var price: Double = 0.0,
    var discount: Double = 0.0,
    val difficulty: CourseDifficultyLevels,
    val category: String? = null,
    val videoLength: Double? = 0.0,

    val featuresBadge: String? = "Ключевые темы",
    val featuresTitle: String? = null,
    val featuresSubtitle: String? = null,
    val featuresDescription: String? = null,
    val features: List<FeatureItemData> = emptyList(),
    val benefitTitle: String? = null,
    val benefitDescription: String? = null,
    val testimonial: TestimonialData? = null,
)

enum class CourseDifficultyLevels {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED
}

data class FeatureItemData(val title: String, val description: String)
data class TestimonialData(
    val quote: String,
    val authorName: String,
    val authorTitle: String,
    val avatarSrc: String? = null,
    val avatarFallback: String
)