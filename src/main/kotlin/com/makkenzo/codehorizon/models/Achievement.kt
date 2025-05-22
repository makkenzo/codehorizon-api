package com.makkenzo.codehorizon.models

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.io.Serializable

enum class AchievementRarity {
    COMMON,
    UNCOMMON,
    RARE,
    EPIC,
    LEGENDARY
}

@Document(collection = "achievements")
data class Achievement(
    @Id val id: String? = null,

    @Indexed(unique = true)
    val key: String,

    val name: String,
    val description: String,
    val iconUrl: String,

    @Indexed
    val triggerTypeKey: String,
    val triggerParameters: Map<String, Any>,

    val xpBonus: Long = 0L,
    val rarity: AchievementRarity = AchievementRarity.COMMON,

    val isGlobal: Boolean = true,
    val order: Int = 0,
    val category: String? = null,

    val isHidden: Boolean = false,
    val prerequisites: List<String> = emptyList()
) : Serializable
