package com.makkenzo.codehorizon.events

import com.makkenzo.codehorizon.models.Achievement
import org.springframework.context.ApplicationEvent

data class AchievementUnlockedEvent(
    val eventSource: Any,
    val userId: String,
    val achievement: Achievement
) : ApplicationEvent(eventSource)