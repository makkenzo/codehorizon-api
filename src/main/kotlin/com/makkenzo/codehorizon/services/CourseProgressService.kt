package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.dtos.PagedResponseDTO
import com.makkenzo.codehorizon.dtos.UserCourseDTO
import com.makkenzo.codehorizon.exceptions.NotFoundException
import com.makkenzo.codehorizon.models.Course
import com.makkenzo.codehorizon.models.CourseProgress
import com.makkenzo.codehorizon.repositories.CourseProgressRepository
import com.makkenzo.codehorizon.repositories.CourseRepository
import com.makkenzo.codehorizon.repositories.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class CourseProgressService(
    private val courseProgressRepository: CourseProgressRepository,
    private val userRepository: UserRepository,
    private val courseService: CourseService,
    private val courseRepository: CourseRepository,
    private val certificateService: CertificateService
) {
    private val logger = LoggerFactory.getLogger(CourseProgressService::class.java)

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

    fun getUserCourseProgress(userId: String, courseId: String): Double? {
        val progress = courseProgressRepository.findByUserIdAndCourseId(userId, courseId)
        return progress?.progress
    }
    
    @Transactional
    fun markLessonAsComplete(userId: String, courseId: String, lessonId: String): CourseProgress {
        val courseProgress = courseProgressRepository.findByUserIdAndCourseId(userId, courseId)
            ?: throw NotFoundException("Прогресс для пользователя $userId и курса $courseId не найден. Возможно, нет доступа?")

        val course = courseRepository.findById(courseId)
            .orElseThrow { NotFoundException("Курс с ID $courseId не найден для расчета прогресса") }

        val totalLessons = course.lessons.size
        if (totalLessons == 0) {
            return courseProgress.copy(progress = 0.0, lastUpdated = Instant.now())
        }

        val updatedCompletedLessons = courseProgress.completedLessons.toMutableSet()
        val added = updatedCompletedLessons.add(lessonId)

        val newProgress = (updatedCompletedLessons.size.toDouble() / totalLessons.toDouble()) * 100.0


        val updatedProgress = courseProgress.copy(
            completedLessons = updatedCompletedLessons.toList(),
            progress = newProgress.coerceIn(0.0, 100.0),
            lastUpdated = Instant.now()
        )

        val savedProgress = courseProgressRepository.save(updatedProgress)

        if (savedProgress.progress >= 100.0) {
            logger.info(
                "Прогресс курса {} для пользователя {} достиг 100%. Попытка создания сертификата.",
                courseId,
                userId
            )
            try {
                certificateService.createCertificateRecord(userId, courseId)
            } catch (e: Exception) {
                logger.error(
                    "Не удалось создать запись о сертификате для курса {} пользователя {}: {}",
                    courseId,
                    userId,
                    e.message,
                    e
                )
            }
        }

        return savedProgress
    }

    fun getUserProgressByCourse(userId: String, courseId: String): CourseProgress? {
        return courseProgressRepository.findByUserIdAndCourseId(userId, courseId)
    }
}