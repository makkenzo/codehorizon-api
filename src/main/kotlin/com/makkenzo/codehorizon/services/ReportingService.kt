package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.dtos.*
import com.makkenzo.codehorizon.models.Course
import com.makkenzo.codehorizon.repositories.CourseRepository
import com.makkenzo.codehorizon.repositories.UserRepository
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.AggregationResults
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class ReportingService(
    private val userRepository: UserRepository,
    private val courseRepository: CourseRepository,
    private val mongoTemplate: MongoTemplate
) {
    fun getDashboardStats(): AdminDashboardStatsDTO {
        val totalUsers = userRepository.count()
        val totalCourses = courseRepository.count()

        val newUsersToday = 5L

        val totalRevenue = 12345.67

        val activeSessions = 15

        return AdminDashboardStatsDTO(
            totalUsers = totalUsers,
            newUsersToday = newUsersToday,
            totalCourses = totalCourses,
            totalRevenue = totalRevenue,
            activeSessions = activeSessions
        )
    }

    fun getChartData(): AdminChartDataDTO {
        val daysToFetch = 30L

        return AdminChartDataDTO(
            userRegistrations = getUserRegistrationsTimeSeries(daysToFetch),
            revenueData = getRevenueTimeSeries(daysToFetch),
            categoryDistribution = getCategoryDistribution(),
            topCoursesByStudents = getTopCoursesByStudents(5)
        )
    }

    private fun getUserRegistrationsTimeSeries(days: Long): List<TimeSeriesDataPointDTO> {
        // TODO: Реализовать агрегацию MongoDB

        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(days - 1)
        return startDate.datesUntil(endDate.plusDays(1)).map { date ->
            TimeSeriesDataPointDTO(date, (5..15).random())
        }.toList()
    }

    // Пример: Доход по дням (заглушка)
    private fun getRevenueTimeSeries(days: Long): List<TimeSeriesDataPointDTO> {
        // TODO: Реализовать агрегацию MongoDB по коллекции покупок (purchases)

        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(days - 1)
        return startDate.datesUntil(endDate.plusDays(1)).map { date ->
            TimeSeriesDataPointDTO(date, (100..500).random().toDouble())
        }.toList()
    }

    private fun getCategoryDistribution(): List<CategoryDistributionDTO> {
        val matchNonNullCategory = Aggregation.match(Criteria.where("category").ne(null).exists(true))

        val groupByCategory = Aggregation.group("category")
            .count().`as`("courseCount")
        val projectResult = Aggregation.project("courseCount")
            .and("_id").`as`("category")

        val aggregation = Aggregation.newAggregation(matchNonNullCategory, groupByCategory, projectResult)

        val results: AggregationResults<CategoryDistributionDTO> =
            mongoTemplate.aggregate(aggregation, Course::class.java, CategoryDistributionDTO::class.java)

        val colors = listOf("#8884d8", "#82ca9d", "#ffc658", "#ff8042", "#d0ed57", "#ffaaa5", "#a8e6cf")
        return results.mappedResults.mapIndexed { index, item ->
            item.copy(fill = colors[index % colors.size])
        }
    }

    // TODO: Нужна реальная метрика популярности (кол-во студентов, покупок)
    private fun getTopCoursesByStudents(limit: Int): List<CoursePopularityDTO> {
        // Заглушка: Берем первые 'limit' курсов
        return courseRepository.findAll(Sort.by(Sort.Direction.DESC, "title"))
            .take(limit)
            .map {
                CoursePopularityDTO(
                    courseTitle = it.title.take(25) + if (it.title.length > 25) "..." else "",
                    studentCount = (50..600).random()
                )
            }
    }
}