package com.makkenzo.codehorizon.models

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document


enum class AchievementTriggerType {
    COURSE_COMPLETION_COUNT,
    LESSON_COMPLETION_STREAK,
    REVIEW_COUNT,
    FIRST_COURSE_COMPLETED,
    FIRST_REVIEW_WRITTEN,
    PROFILE_COMPLETION_PERCENT,
    DAILY_LOGIN_STREAK,
    COURSE_CREATION_COUNT,
    TOTAL_XP_EARNED,
    LEVEL_REACHED
}

@Document(collection = "achievements")
data class Achievement(
    @Id val id: String? = null,
    @Indexed(unique = true)
    val key: String,
    val name: String,
    val description: String,
    val iconUrl: String,
    val triggerType: AchievementTriggerType,
    val triggerThreshold: Int,
    val xpBonus: Long = 0L,
    val isGlobal: Boolean = true,
    val order: Int = 0,
    val category: String? = null,
)
