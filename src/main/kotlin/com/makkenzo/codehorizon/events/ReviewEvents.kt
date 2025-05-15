package com.makkenzo.codehorizon.events

import org.springframework.context.ApplicationEvent

data class ReviewCreatedEvent(
    val eventSource: Any,
    val authorId: String,
    val reviewId: String,
    val courseId: String
) : ApplicationEvent(eventSource)