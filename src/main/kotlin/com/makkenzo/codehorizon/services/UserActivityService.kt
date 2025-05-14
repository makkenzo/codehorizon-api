package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.models.UserDailyActivityStats
import com.makkenzo.codehorizon.repositories.UserDailyActivityStatsRepository
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class UserActivityService(private val userDailyActivityStatsRepository: UserDailyActivityStatsRepository) {
    fun incrementLessonsCompletedToday(userId: String): UserDailyActivityStats {
        val today = LocalDate.now()
        val stats = userDailyActivityStatsRepository.findByUserIdAndDate(userId, today)
            ?: UserDailyActivityStats(userId = userId, date = today)

        stats.lessonsCompletedCount++
        return userDailyActivityStatsRepository.save(stats)
    }
}