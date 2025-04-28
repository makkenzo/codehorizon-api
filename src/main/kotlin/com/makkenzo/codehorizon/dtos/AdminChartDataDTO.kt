package com.makkenzo.codehorizon.dtos

import java.time.LocalDate

data class TimeSeriesDataPointDTO(
    val date: LocalDate,
    val value: Number
)

data class AdminChartDataDTO(
    val userRegistrations: List<TimeSeriesDataPointDTO>,
    val revenueData: List<TimeSeriesDataPointDTO>,
    val categoryDistribution: List<CategoryDistributionDTO>,
    val topCoursesByStudents: List<CoursePopularityDTO>
)