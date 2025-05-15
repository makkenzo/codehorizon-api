package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.dtos.PagedResponseDTO
import com.makkenzo.codehorizon.dtos.UserCourseDTO
import com.makkenzo.codehorizon.events.CourseCompletedEvent
import com.makkenzo.codehorizon.events.LessonCompletedEvent
import com.makkenzo.codehorizon.exceptions.NotFoundException
import com.makkenzo.codehorizon.models.CourseProgress
import com.makkenzo.codehorizon.models.NotificationType
import com.makkenzo.codehorizon.repositories.CourseProgressRepository
import com.makkenzo.codehorizon.repositories.CourseRepository
import com.makkenzo.codehorizon.repositories.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class CourseProgressService(
    private val courseProgressRepository: CourseProgressRepository,
    private val userRepository: UserRepository,
    private val courseService: CourseService,
    private val courseRepository: CourseRepository,
    private val certificateService: CertificateService,
    private val authorizationService: AuthorizationService,
    private val notificationService: NotificationService,
    private val userService: UserService,
    private val eventPublisher: ApplicationEventPublisher,
    private val userActivityService: UserActivityService
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
    fun markLessonAsComplete(courseId: String, lessonId: String): CourseProgress {
        val currentUserId = authorizationService.getCurrentUserEntity().id!!
        val courseProgress = courseProgressRepository.findByUserIdAndCourseId(currentUserId, courseId)
            ?: throw NotFoundException("Прогресс для пользователя $currentUserId и курса $courseId не найден. Возможно, нет доступа?")

        val courseEntity = courseRepository.findById(courseId).orElse(null)

        val totalLessons = courseEntity.lessons.size
        if (totalLessons == 0) {
            return courseProgressRepository.save(courseProgress.copy(lastUpdated = Instant.now()))
        }

        val updatedCompletedLessons = courseProgress.completedLessons.toMutableSet()
        val wasAlreadyCompleted = updatedCompletedLessons.contains(lessonId)
        val addedNewCompletion = updatedCompletedLessons.add(lessonId)

        if (wasAlreadyCompleted) {
            logger.info(
                "Урок {} в курсе {} уже был отмечен как пройденный для пользователя {}. Обновляем только lastUpdated.",
                lessonId,
                courseId,
                currentUserId
            )
            return courseProgressRepository.save(courseProgress.copy(lastUpdated = Instant.now()))
        }

        val newProgressValue = (updatedCompletedLessons.size.toDouble() / totalLessons.toDouble()) * 100.0

        val updatedProgress = courseProgress.copy(
            completedLessons = updatedCompletedLessons.toList(),
            progress = newProgressValue.coerceIn(0.0, 100.0),
            lastUpdated = Instant.now()
        )

        val savedProgress = courseProgressRepository.save(updatedProgress)

        val student = userRepository.findById(currentUserId).orElse(null)
        val lessonEntity = courseEntity.lessons.find { it.id == lessonId }

        if (addedNewCompletion) {
            if (student != null && lessonEntity != null) {
                userService.gainXp(
                    currentUserId,
                    UserService.XP_FOR_LESSON_COMPLETION,
                    "Завершение урока: ${lessonEntity.title}"
                )
                userService.recordLessonCompletionActivity(currentUserId)
            }

            eventPublisher.publishEvent(LessonCompletedEvent(this, currentUserId, lessonId, courseId))
            userActivityService.incrementLessonsCompletedToday(currentUserId)
        }

        if (savedProgress.progress >= 100.0 && courseProgress.progress < 100.0) {
            val courseTitle = courseEntity.title
            val courseSlug = courseEntity.slug

            notificationService.createNotification(
                userId = currentUserId,
                type = NotificationType.COURSE_COMPLETED,
                message = "Поздравляем! Вы успешно завершили курс \"$courseTitle\"!",
                link = "/courses/$courseSlug"
            )

            if (courseEntity.authorId != currentUserId) {
                val authorUser = userRepository.findById(courseEntity.authorId).orElse(null)
                if (authorUser != null) {
                    notificationService.createNotification(
                        userId = courseEntity.authorId,
                        type = NotificationType.COURSE_COMPLETED_BY_STUDENT,
                        message = "Пользователь ${student?.username ?: "студент"} завершил ваш курс \"${courseEntity.title}\".",
                        link = "/admin/courses/${courseEntity.id}/students"
                    )
                }
            }

            try {
                certificateService.createCertificateRecord(currentUserId, courseId)
            } catch (e: Exception) {
                logger.error(
                    "Не удалось создать запись о сертификате для курса {} пользователя {}: {}",
                    courseId, currentUserId, e.message, e
                )
            }

            if (student != null) {
                userService.gainXp(
                    currentUserId,
                    UserService.XP_FOR_COURSE_COMPLETION,
                    "Завершение курса: ${courseEntity.title}"
                )
                eventPublisher.publishEvent(
                    CourseCompletedEvent(
                        this,
                        currentUserId,
                        student.username,
                        student.email,
                        courseId,
                        courseEntity.title,
                        courseEntity.slug,
                        courseEntity.authorId
                    )
                )
            }
        }

        return savedProgress
    }

    fun getUserProgressByCourse(courseId: String): CourseProgress? {
        val currentUserId = authorizationService.getCurrentUserEntity().id!!
        return courseProgressRepository.findByUserIdAndCourseId(currentUserId, courseId)
    }
}