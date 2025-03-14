package com.makkenzo.codehorizon.models

import com.makkenzo.codehorizon.dtos.CourseDTO
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "courses")
data class Course(
    @Id val id: String? = null,
    var title: String,
    var description: String? = null,
    val imagePreview: String? = null,
    val videoPreview: String? = null,
    val authorId: String,
    var lessons: MutableList<Lesson> = mutableListOf(),
    var rating: Double = 0.0,
    var price: Double = 0.0,
    var discount: Double = 0.0,
    val difficulty: CourseDifficultyLevels,
) {
    fun toDto(): CourseDTO {
        return CourseDTO(
            id = this.id ?: "",
            title = this.title,
            description = this.description,
            imagePreview = this.imagePreview,
            videoPreview = this.videoPreview,
            authorId = this.authorId,
            rating = this.rating,
            price = this.price,
            discount = this.discount,
            difficulty = this.difficulty
        )
    }
}

enum class CourseDifficultyLevels {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED
}