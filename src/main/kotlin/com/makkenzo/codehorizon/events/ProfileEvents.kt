package com.makkenzo.codehorizon.events

import org.springframework.context.ApplicationEvent

data class ProfileUpdatedEvent(
    val eventSource: Any,
    val userId: String,
    val completionPercentage: Int
) : ApplicationEvent(eventSource)