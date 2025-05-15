package com.makkenzo.codehorizon.events

import org.springframework.context.ApplicationEvent

data class LessonCompletedEvent(
    val eventSource: Any,
    val userId: String,
    val lessonId: String,
    val courseId: String
) : ApplicationEvent(eventSource)