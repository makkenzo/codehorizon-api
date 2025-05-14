package com.makkenzo.codehorizon.models

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant


@Document(collection = "user_achievements")
@CompoundIndex(name = "user_achievement_idx", def = "{'userId': 1, 'achievementKey': 1}", unique = true)
data class UserAchievement(
    @Id val id: String? = null,
    val userId: String,
    val achievementKey: String,
    val earnedAt: Instant = Instant.now()
)