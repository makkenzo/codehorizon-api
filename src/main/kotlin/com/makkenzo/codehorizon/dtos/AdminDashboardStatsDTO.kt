package com.makkenzo.codehorizon.dtos

data class AdminDashboardStatsDTO(
    val totalUsers: Long,
    val newUsersToday: Long,
    val totalCourses: Long,
    val totalRevenue: Double,
    val activeSessions: Int
)