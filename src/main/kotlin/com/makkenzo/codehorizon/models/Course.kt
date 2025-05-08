package com.makkenzo.codehorizon.models

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.io.Serializable
import java.time.Instant

@Document(collection = "courses")
data class Course(
    @Id val id: String? = null,
    var title: String,
    @Indexed(unique = true)
    var slug: String,
    var description: String? = null,
    val imagePreview: String? = null,
    val videoPreview: String? = null,
    @Indexed
    val authorId: String,
    var lessons: MutableList<Lesson> = mutableListOf(),
    @Indexed
    var rating: Double = 0.0,
    @Indexed
    var price: Double = 0.0,
    var discount: Double = 0.0,
    @Indexed
    val difficulty: CourseDifficultyLevels,
    @Indexed
    val category: String? = null,
    val videoLength: Double? = 0.0,

    val featuresBadge: String? = null,
    val featuresTitle: String? = null,
    val featuresSubtitle: String? = null,
    val featuresDescription: String? = null,
    val features: List<FeatureItemData> = emptyList(),
    val benefitTitle: String? = null,
    val benefitDescription: String? = null,
    val testimonial: TestimonialData? = null,

    @Indexed
    val createdAt: Instant = Instant.now(),
    @Indexed
    val deletedAt: Instant? = null
) : Serializable

enum class CourseDifficultyLevels {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED
}

data class FeatureItemData(val title: String, val description: String) : Serializable
data class TestimonialData(
    val quote: String,
    val authorName: String,
    val authorTitle: String,
    val avatarSrc: String? = null,
    val avatarFallback: String? = null
) : Serializable