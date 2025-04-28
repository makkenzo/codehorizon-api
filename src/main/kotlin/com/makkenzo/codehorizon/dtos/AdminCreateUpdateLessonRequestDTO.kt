package com.makkenzo.codehorizon.dtos

import com.makkenzo.codehorizon.models.Attachment
import com.makkenzo.codehorizon.models.Task

data class AdminCreateUpdateLessonRequestDTO(
    val title: String,
    val content: String?,
    val codeExamples: List<String>? = emptyList(),
    val tasks: List<Task>? = emptyList(),
    val attachments: List<Attachment>? = emptyList(),
    val mainAttachment: String? = null
)
