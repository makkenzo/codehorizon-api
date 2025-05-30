package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.dtos.*
import com.makkenzo.codehorizon.models.Course
import com.makkenzo.codehorizon.models.CourseProgress
import com.makkenzo.codehorizon.models.Purchase
import com.makkenzo.codehorizon.models.User
import com.makkenzo.codehorizon.repositories.CourseProgressRepository
import com.makkenzo.codehorizon.repositories.CourseRepository
import com.makkenzo.codehorizon.repositories.UserRepository
import org.bson.Document
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation.*
import org.springframework.data.mongodb.core.aggregation.AggregationResults
import org.springframework.data.mongodb.core.aggregation.ConvertOperators
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.stream.Stream

@Service
class ReportingService(
    private val userRepository: UserRepository,
    private val courseRepository: CourseRepository,
    private val mongoTemplate: MongoTemplate,
    private val courseProgressRepository: CourseProgressRepository
) {
    fun getDashboardStats(): AdminDashboardStatsDTO {
        val totalUsers = userRepository.count()
        val totalCourses = courseRepository.count()

        val startOfDay = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC)
        val startOfTomorrow = LocalDate.now().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        val newUsersToday = userRepository.countByCreatedAtBetween(startOfDay, startOfTomorrow)

        val matchStage = match(Criteria.where("amount").exists(true).ne(null))
        val groupStage = group()
            .sum("amount").`as`("totalRevenueLong")
        val aggregation = newAggregation(matchStage, groupStage)

        val revenueResult = mongoTemplate.aggregate(
            aggregation,
            Purchase::class.java,
            Document::class.java
        ).uniqueMappedResult

        val totalRevenueLong = revenueResult?.getLong("totalRevenueLong") ?: 0L
        val totalRevenue = totalRevenueLong / 100.0

        val completedCoursesCount = try {
            courseProgressRepository.countByProgressGreaterThanEqual(100.0)
        } catch (e: Exception) {
            println("WARN: Failed to count completed courses: ${e.message}. Falling back to 0.")
            0L
        }

        return AdminDashboardStatsDTO(
            totalUsers = totalUsers,
            newUsersToday = newUsersToday,
            totalCourses = totalCourses,
            totalRevenue = totalRevenue,
            completedCoursesCount = completedCoursesCount
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
        if (days <= 0) return emptyList()

        val startDate = LocalDate.now().minusDays(days - 1).atStartOfDay()


        val matchStage = match(Criteria.where("createdAt").gte(startDate.toInstant(ZoneOffset.UTC)))
        val projectDateStage = project()
            .and { _ ->
                Document(
                    "\$dateToString", Document()
                        .append("format", "%Y-%m-%d")
                        .append("date", "\$createdAt")
                        .append("timezone", "UTC")
                )
            }.`as`("registrationDate")

        val groupStage = group("registrationDate").count().`as`("count")
        val projectResultStage = project("count").and("_id").`as`("dateStr").andExclude("_id")
        val sortStage = sort(Sort.Direction.ASC, "dateStr")

        val aggregation = newAggregation(matchStage, projectDateStage, groupStage, projectResultStage, sortStage)
        val aggregationResults = mongoTemplate.aggregate(aggregation, User::class.java, Document::class.java)

        val resultsDTO = aggregationResults.mappedResults.mapNotNull { doc ->
            val dateStr = doc.getString("dateStr")
            val value = doc.getInteger("count", 0)
            try {
                val date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
                TimeSeriesDataPointDTO(date = date, value = value)
            } catch (e: Exception) {
                println("WARN: Could not parse date string '$dateStr' from user registration aggregation.")
                null
            }
        }

        val resultMap: Map<LocalDate, TimeSeriesDataPointDTO> = resultsDTO.associateBy { it.date }
        val finalTimeSeries = mutableListOf<TimeSeriesDataPointDTO>()

        val startDateTime: LocalDateTime = LocalDate.now().minusDays(days - 1).atStartOfDay()

        Stream.iterate(startDateTime) { dateTime -> dateTime.plusDays(1) }
            .limit(days)
            .forEach { currentDateTime: LocalDateTime ->
                val currentDate = currentDateTime.toLocalDate()
                finalTimeSeries.add(
                    resultMap.getOrDefault(currentDate, TimeSeriesDataPointDTO(date = currentDate, value = 0))
                )
            }

        return finalTimeSeries
    }

    private fun getRevenueTimeSeries(days: Long): List<TimeSeriesDataPointDTO> {
        if (days <= 0) return emptyList()

        val startDate = LocalDate.now().minusDays(days - 1)
        val endDate = LocalDate.now()

        val matchStage = match(
            Criteria.where("createdAt").gte(startDate.atStartOfDay().toInstant(ZoneOffset.UTC))
                .and("amount").exists(true).ne(null)
                .and("currency").exists(true).ne(null)
        )

        val projectDateStage = project("amount")
            .and { _ ->
                Document(
                    "\$dateToString", Document()
                        .append("format", "%Y-%m-%d")
                        .append("date", "\$createdAt")
                        .append("timezone", "UTC")
                )
            }.`as`("purchaseDateStr")

        val groupStage = group("purchaseDateStr")
            .sum("amount").`as`("dailyRevenueLong")

        val projectResultStage = project("dailyRevenueLong")
            .and("_id").`as`("dateStr")
            .andExclude("_id")

        val sortStage = sort(Sort.Direction.ASC, "dateStr")

        val aggregation = newAggregation(
            matchStage,
            projectDateStage,
            groupStage,
            projectResultStage,
            sortStage
        )

        val aggregationResults = mongoTemplate.aggregate(
            aggregation,
            Purchase::class.java,
            Document::class.java
        )

        val resultsDTO = aggregationResults.mappedResults.mapNotNull { doc ->
            val dateStr = doc.getString("dateStr")
            val valueLong = doc.getLong("dailyRevenueLong") ?: return@mapNotNull null

            val valueDouble = valueLong / 100.0

            try {
                val date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
                TimeSeriesDataPointDTO(date = date, value = valueDouble)
            } catch (e: Exception) {
                println("WARN: Could not parse date string '$dateStr' from revenue aggregation.")
                null
            }
        }

        val resultMap = resultsDTO.associateBy { it.date }
        val finalTimeSeries = mutableListOf<TimeSeriesDataPointDTO>()

        Stream.iterate(startDate) { date -> date.plusDays(1) }
            .limit(days)
            .forEach { currentDate: LocalDate ->
                finalTimeSeries.add(
                    resultMap.getOrDefault(currentDate, TimeSeriesDataPointDTO(date = currentDate, value = 0.0))
                )
            }

        return finalTimeSeries
    }

    private fun getCategoryDistribution(): List<CategoryDistributionDTO> {
        val matchNonNullCategory = match(Criteria.where("category").ne(null).exists(true))
        val groupByCategory = group("category").count().`as`("courseCount")
        val projectResult = project("courseCount").and("_id").`as`("category")
        val aggregation = newAggregation(matchNonNullCategory, groupByCategory, projectResult)
        val results: AggregationResults<CategoryDistributionDTO> =
            mongoTemplate.aggregate(aggregation, Course::class.java, CategoryDistributionDTO::class.java)

        val colors = listOf("#8884d8", "#82ca9d", "#ffc658", "#ff8042", "#d0ed57", "#ffaaa5", "#a8e6cf")
        return results.mappedResults.mapIndexed { index, item ->
            item.copy(fill = colors.getOrElse(index % colors.size) { "#cccccc" })
        }
    }

    private fun getTopCoursesByStudents(limit: Int): List<CoursePopularityDTO> {
        if (limit <= 0) return emptyList()

        val groupStage = group("courseId")
            .addToSet("userId").`as`("uniqueUserIds")

        val projectCountStage = project()
            .and("_id").`as`("courseId")
            .and("uniqueUserIds").size().`as`("studentCount")

        val sortStage = sort(Sort.Direction.DESC, "studentCount")
        val limitStage = limit(limit.toLong())

        val convertToObjectIdStage = project("studentCount", "courseId")
            .and(ConvertOperators.ToObjectId.toObjectId("\$courseId")).`as`("courseObjectId")

        val lookupStage = lookup("courses", "courseObjectId", "_id", "courseInfo")

        val unwindStage = unwind("courseInfo", true)

        val matchCourseExistsStage = match(Criteria.where("courseInfo").exists(true))

        val projectResultStage = project("studentCount")
            .and("courseInfo.title").`as`("courseTitle")
            .andExclude("_id")

        val aggregation = newAggregation(
            groupStage,
            projectCountStage,
            sortStage,
            limitStage,
            convertToObjectIdStage,
            lookupStage,
            unwindStage,
            matchCourseExistsStage,
            projectResultStage
        )

        val aggregationResults = mongoTemplate.aggregate(
            aggregation,
            CourseProgress::class.java,
            CoursePopularityDTO::class.java
        )

        return aggregationResults.mappedResults
    }
}