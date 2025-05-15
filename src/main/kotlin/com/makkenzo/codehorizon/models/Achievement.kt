package com.makkenzo.codehorizon.models

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

enum class AchievementRarity {
    COMMON,
    UNCOMMON,
    RARE,
    EPIC,
    LEGENDARY
}

enum class AchievementTriggerType {
    COURSE_COMPLETION_COUNT,
    LESSON_COMPLETION_COUNT_TOTAL,
    LESSON_COMPLETION_STREAK_DAILY,
    REVIEW_COUNT,
    FIRST_COURSE_COMPLETED,
    FIRST_REVIEW_WRITTEN,
    PROFILE_COMPLETION_PERCENT,
    DAILY_LOGIN_STREAK,
    COURSE_CREATION_COUNT,
    TOTAL_XP_EARNED,
    LEVEL_REACHED,
    SPECIFIC_COURSE_COMPLETED,
    SPECIFIC_LESSON_COMPLETED,
    CATEGORY_COURSES_COMPLETED,
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
    val triggerThresholdValue: String? = null,

    val xpBonus: Long = 0L,
    val rarity: AchievementRarity = AchievementRarity.COMMON,

    val isGlobal: Boolean = true,
    val order: Int = 0,
    val category: String? = null,

    val isHidden: Boolean = false,
    val prerequisites: List<String> = emptyList()
)
