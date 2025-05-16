package com.makkenzo.codehorizon.dtos

import com.fasterxml.jackson.annotation.JsonIgnore
import com.makkenzo.codehorizon.models.Lesson
import com.makkenzo.codehorizon.models.Task
import java.util.*

data class LessonRequestDTO(
    @JsonIgnore
    var id: String? = null,
    val title: String,
    var content: String,
    var codeExamples: List<String> = emptyList(),
    var tasks: MutableList<Task> = mutableListOf()
) {
    fun toLesson(): Lesson {
        return Lesson(
            id = id ?: UUID.randomUUID().toString(),
            title = title,
            content = content,
            codeExamples = codeExamples,
            tasks = tasks
        )
    }
}