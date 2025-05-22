package com.makkenzo.codehorizon.repositories

import com.makkenzo.codehorizon.models.AchievementTriggerDefinition
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface AchievementTriggerDefinitionRepository : MongoRepository<AchievementTriggerDefinition, String> {
    fun findByKey(key: String): AchievementTriggerDefinition?
    fun existsByKey(key: String): Boolean
}
