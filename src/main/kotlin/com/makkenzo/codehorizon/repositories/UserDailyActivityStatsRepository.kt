package com.makkenzo.codehorizon.repositories

import com.makkenzo.codehorizon.models.UserDailyActivityStats
import org.springframework.data.mongodb.repository.MongoRepository
import java.time.LocalDate

interface UserDailyActivityStatsRepository : MongoRepository<UserDailyActivityStats, String> {
    fun findByUserIdAndDate(userId: String, date: LocalDate): UserDailyActivityStats?
}