package com.makkenzo.codehorizon.dtos

import com.makkenzo.codehorizon.models.Attachment
import com.makkenzo.codehorizon.models.Task
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class AdminCreateUpdateLessonRequestDTO(
    @field:NotBlank(message = "Название урока не может быть пустым")
    @field:Size(max = 255, message = "Название урока не должно превышать 255 символов")
    val title: String,

    @field:Size(max = 100000, message = "Содержимое урока слишком большое")
    val content: String?,

    val codeExamples: List<String>? = emptyList(),

    val tasks: List<@Valid Task>? = emptyList(),
    val attachments: List<@Valid Attachment>? = emptyList(),
    
    val mainAttachment: String? = null
)