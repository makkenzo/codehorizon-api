package com.makkenzo.codehorizon.models

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "achievement_trigger_definitions")
data class AchievementTriggerDefinition(
    @Id
    val id: String? = null,

    @Indexed(unique = true)
    val key: String,

    val name: String,
    val description: String,
    val parametersSchema: Map<String, String>,

    val createdAt: Instant? = null,
    val updatedAt: Instant? = null
)
