package com.makkenzo.codehorizon.dtos

import com.makkenzo.codehorizon.models.CourseDifficultyLevels
import com.makkenzo.codehorizon.models.FeatureItemData
import com.makkenzo.codehorizon.models.TestimonialData
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size

data class AdminCreateUpdateCourseRequestDTO(
    @field:NotBlank(message = "Название курса не может быть пустым")
    @field:Size(max = 255, message = "Название курса не должно превышать 255 символов")
    val title: String,

    @field:Size(max = 5000, message = "Описание курса не должно превышать 5000 символов")
    val description: String?,

    @field:NotNull(message = "Цена не может быть null")
    @field:PositiveOrZero(message = "Цена должна быть положительной или нулем")
    val price: Double,

    @field:PositiveOrZero(message = "Скидка должна быть положительной или нулем")
    val discount: Double? = 0.0,
    
    val isFree: Boolean? = false,

    @field:NotNull(message = "Уровень сложности не может быть null")
    val difficulty: CourseDifficultyLevels,

    @field:Size(max = 100, message = "Категория не должна превышать 100 символов")
    val category: String?,

    @field:NotBlank(message = "ID автора не может быть пустым")
    val authorId: String,

    val imagePreview: String? = null,
    val videoPreview: String? = null,

    @field:Size(max = 100, message = "Бейдж особенностей не должен превышать 100 символов")
    val featuresBadge: String? = null,
    @field:Size(max = 255, message = "Заголовок особенностей не должен превышать 255 символов")
    val featuresTitle: String? = null,
    @field:Size(max = 500, message = "Подзаголовок особенностей не должен превышать 500 символов")
    val featuresSubtitle: String? = null,
    @field:Size(max = 2000, message = "Описание особенностей не должно превышать 2000 символов")
    val featuresDescription: String? = null,

    val features: List<FeatureItemData>? = null,

    @field:Size(max = 255, message = "Заголовок преимуществ не должен превышать 255 символов")
    val benefitTitle: String? = null,
    @field:Size(max = 2000, message = "Описание преимуществ не должно превышать 2000 символов")
    val benefitDescription: String? = null,

    val testimonial: @Valid TestimonialData? = null
)
