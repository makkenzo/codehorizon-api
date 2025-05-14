package com.makkenzo.codehorizon.repositories

import com.makkenzo.codehorizon.models.UserAchievement
import org.springframework.data.mongodb.repository.MongoRepository

interface UserAchievementRepository : MongoRepository<UserAchievement, String> {
    fun findByUserId(userId: String): List<UserAchievement>
    fun existsByUserIdAndAchievementKey(userId: String, achievementKey: String): Boolean
    fun findByUserIdAndAchievementKeyIn(userId: String, achievementKeys: List<String>): List<UserAchievement>
}