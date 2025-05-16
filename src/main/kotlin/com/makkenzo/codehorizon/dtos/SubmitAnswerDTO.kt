package com.makkenzo.codehorizon.dtos

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.io.Serializable

data class SubmitAnswerDTO(
    @field:NotBlank(message = "ID курса не может быть пустым")
    val courseId: String,

    @field:NotBlank(message = "ID урока не может быть пустым")
    val lessonId: String,

    @field:NotBlank(message = "ID задачи не может быть пустым")
    val taskId: String,

    val language: String? = null,

    @field:Size(max = 20000, message = "Код ответа слишком длинный (макс 20000 символов)")
    val answerCode: String? = null,

    @field:Size(max = 5000, message = "Текстовый ответ слишком длинный (макс 5000 символов)")
    val answerText: String? = null,
) : Serializable
