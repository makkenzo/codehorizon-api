package com.makkenzo.codehorizon.dtos

import java.time.Instant

data class GlobalAchievementDTO(
    val id: String,
    val key: String,
    val name: String,
    val description: String,
    val iconUrl: String?,
    val xpBonus: Long,
    val order: Int,
    val category: String?,
    val isEarnedByUser: Boolean,
    val earnedAt: Instant?,
)