package com.makkenzo.codehorizon.repositories

import com.makkenzo.codehorizon.models.Achievement
import com.makkenzo.codehorizon.models.AchievementTriggerType
import org.springframework.data.mongodb.repository.MongoRepository

interface AchievementRepository : MongoRepository<Achievement, String> {
    fun findByKey(key: String): Achievement?
    fun findByTriggerType(triggerType: AchievementTriggerType): List<Achievement>
    fun findByKeyIn(keys: List<String>): List<Achievement>
}