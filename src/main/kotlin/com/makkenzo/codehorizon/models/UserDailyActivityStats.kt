package com.makkenzo.codehorizon.models

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDate

@Document(collection = "user_daily_activity_stats")
@CompoundIndex(name = "user_date_idx", def = "{'userId': 1, 'date': 1}", unique = true)
data class UserDailyActivityStats(
    @Id val id: String? = null,
    val userId: String,
    val date: LocalDate,
    var lessonsCompletedCount: Int = 0
)
