package com.makkenzo.codehorizon.events

import org.springframework.context.ApplicationEvent

data class UserLoggedInEvent(
    val eventSource: Any,
    val userId: String
) : ApplicationEvent(eventSource)