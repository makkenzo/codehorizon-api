package com.makkenzo.codehorizon.dtos

import com.makkenzo.codehorizon.models.*
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

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

data class AdminTestCaseDTO(
    @field:NotBlank(message = "ID тест-кейса не может быть пустым")
    val id: String,

    @field:NotBlank(message = "Имя тест-кейса не может быть пустым")
    @field:Size(max = 255, message = "Имя тест-кейса не должно превышать 255 символов")
    val name: String,

    val input: List<String>? = emptyList(),

    val expectedOutput: List<String>? = emptyList(),

    val isHidden: Boolean = false,

    val points: Int = 1
)

data class AdminTaskDTO(
    @field:NotBlank(message = "ID задачи не может быть пустым")
    val id: String,

    @field:NotBlank(message = "Описание задачи не может быть пустым")
    @field:Size(max = 10000, message = "Описание задачи слишком длинное")
    val description: String,

    val solution: String? = null,

    val taskType: TaskType,

    @field:Valid
    val options: List<String>? = null,

    @field:Size(max = 50, message = "Название языка не должно превышать 50 символов")
    val language: String? = null,

    @field:Size(max = 10000, message = "Шаблонный код слишком длинный")
    val boilerplateCode: String? = null,

    @field:Valid
    val testCases: List<AdminTestCaseDTO>? = emptyList()
)