package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.dtos.PagedResponseDTO
import com.makkenzo.codehorizon.dtos.UserCourseDTO
import com.makkenzo.codehorizon.models.CourseProgress
import com.makkenzo.codehorizon.repositories.CourseProgressRepository
import com.makkenzo.codehorizon.repositories.UserRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

@Service
class CourseProgressService(
    private val courseProgressRepository: CourseProgressRepository,
    private val userRepository: UserRepository,
    private val courseService: CourseService
) {
    fun addCourseProgress(userId: String, courseId: String): CourseProgress {
        val doc = courseProgressRepository.findByUserIdAndCourseId(userId, courseId)

        if (doc == null) {
            val newProgress = CourseProgress(userId = userId, courseId = courseId)
            courseProgressRepository.save(newProgress)
            return newProgress
        } else {
            throw Error("progress already exists")
        }
    }

    fun getUserCoursesWithProgress(userId: String, pageable: Pageable): PagedResponseDTO<UserCourseDTO> {
        val progressPage = courseProgressRepository.findByUserId(userId, pageable)
        val progressList = progressPage.content
        val courseIds = progressList.map { it.courseId }

        val courses = courseService.findByIds(courseIds)

        val userCourses = courses.map { course ->
            val progress = progressList.find { it.courseId == course.id }?.progress ?: 0.0
            UserCourseDTO(course = course, progress = progress)
        }

        return PagedResponseDTO(
            content = userCourses,
            pageNumber = progressPage.number,
            pageSize = progressPage.size,
            totalElements = progressPage.totalElements,
            isLast = progressPage.isLast,
            totalPages = progressPage.totalPages
        )
    }
}