package com.makkenzo.codehorizon.events

import org.springframework.context.ApplicationEvent

data class UserLeveledUpEvent(
    val eventSource: Any,
    val userId: String,
    val newLevel: Int,
    val oldLevel: Int
) : ApplicationEvent(eventSource)