package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.dtos.AuthorCourseAnalyticsDTO
import com.makkenzo.codehorizon.dtos.AuthorCourseListItemAnalyticsDTO
import com.makkenzo.codehorizon.dtos.PagedResponseDTO
import com.makkenzo.codehorizon.exceptions.NotFoundException
import com.makkenzo.codehorizon.repositories.CourseProgressRepository
import com.makkenzo.codehorizon.repositories.CourseRepository
import com.makkenzo.codehorizon.repositories.ReviewRepository
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class AuthorAnalyticsService(
    private val courseRepository: CourseRepository,
    private val courseProgressRepository: CourseProgressRepository,
    private val reviewRepository: ReviewRepository,
    private val authorizationService: AuthorizationService
) {
    fun getCourseAnalyticsForAuthor(courseId: String): AuthorCourseAnalyticsDTO {
        val currentUser = authorizationService.getCurrentUserEntity()
        val course = courseRepository.findById(courseId)
            .orElseThrow { NotFoundException("Курс $courseId не найден") }

        if (course.authorId != currentUser.id) {
            throw AccessDeniedException("У вас нет прав для просмотра аналитики этого курса.")
        }

        val totalEnrolledStudents = courseProgressRepository.countByCourseId(courseId)
        val progressRecords = courseProgressRepository.findByCourseId(courseId)

        val averageCompletionRate = if (progressRecords.isNotEmpty()) {
            progressRecords.map { it.progress }.average()
        } else 0.0

        val thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS)
        val activeStudentsLast30Days =
            courseProgressRepository.countByCourseIdAndLastUpdatedAfter(courseId, thirtyDaysAgo)

        val totalReviews = reviewRepository.countByCourseId(courseId)

        return AuthorCourseAnalyticsDTO(
            courseId = course.id!!,
            courseTitle = course.title,
            totalEnrolledStudents = totalEnrolledStudents,
            activeStudentsLast30Days = activeStudentsLast30Days,
            averageCompletionRate = averageCompletionRate,
            averageRating = course.rating,
            totalReviews = totalReviews
        )
    }

    fun getAuthorCoursesWithAnalytics(pageable: Pageable): PagedResponseDTO<AuthorCourseListItemAnalyticsDTO> {
        val currentUser = authorizationService.getCurrentUserEntity()
        val authorId = currentUser.id!!

        val allAuthorCourses = courseRepository.findByAuthorIdAndDeletedAtIsNull(authorId)
        val totalElements = allAuthorCourses.size.toLong()

        val start = pageable.offset.toInt()
        val end = (start + pageable.pageSize).coerceAtMost(allAuthorCourses.size)
        val paginatedCourses = if (start <= end) allAuthorCourses.subList(start, end) else emptyList()

        val coursesAnalyticsItems = paginatedCourses.map { course ->
            val totalEnrolledStudents = courseProgressRepository.countByCourseId(course.id!!)
            val progressRecords = courseProgressRepository.findByCourseId(course.id)
            val averageCompletionRate = if (progressRecords.isNotEmpty()) {
                progressRecords.map { it.progress }.average()
            } else 0.0

            AuthorCourseListItemAnalyticsDTO(
                courseId = course.id,
                courseTitle = course.title,
                slug = course.slug,
                totalEnrolledStudents = totalEnrolledStudents,
                averageCompletionRate = averageCompletionRate,
                averageRating = course.rating,
                imagePreview = course.imagePreview
            )
        }

        val page = PageImpl(coursesAnalyticsItems, pageable, totalElements)

        return PagedResponseDTO(
            content = page.content,
            pageNumber = page.number,
            pageSize = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            isLast = page.isLast
        )
    }
}