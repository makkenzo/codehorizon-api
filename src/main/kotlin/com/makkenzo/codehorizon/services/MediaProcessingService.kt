package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.models.Course
import com.makkenzo.codehorizon.repositories.CourseRepository
import com.makkenzo.codehorizon.utils.MediaUtils
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MediaProcessingService(
    private val courseRepository: CourseRepository,
    private val mongoTemplate: MongoTemplate
) {
    private val logger = LoggerFactory.getLogger(MediaProcessingService::class.java)

    @Async
    @Transactional
    fun updateCourseVideoLengthAsync(courseId: String) {
        try {
            val course = courseRepository.findById(courseId).orElse(null)
            if (course == null) {
                logger.warn("Асинхронное обновление длительности: Курс с ID {} не найден.", courseId)
                return
            }

            val videoUrls = mutableListOf<String>()
            course.videoPreview?.let { videoUrls.add(it) }
            course.lessons.mapNotNull { it.mainAttachment }.forEach { videoUrls.add(it) }

            var totalLength = 0.0
            videoUrls.forEach { url ->
                try {
                    totalLength += MediaUtils.getVideoDuration(url)
                } catch (e: Exception) {
                    logger.error("Ошибка при получении длительности для URL {} курса {}: {}", url, courseId, e.message)
                }
            }

            if (course.videoLength != totalLength) {
                val updatedCourse = course.copy(videoLength = totalLength)
                courseRepository.save(updatedCourse)
                logger.info(
                    "Асинхронно обновлена длительность видео для курса '{}' ({}): {}s",
                    course.title,
                    courseId,
                    totalLength
                )
            }
        } catch (e: Exception) {
            logger.error(
                "Критическая ошибка в асинхронном обновлении длительности видео для курса {}: {}",
                courseId,
                e.message,
                e
            )
        }
    }

    fun updateVideoLengthForAllCoursesWithZeroOrNull() {
        logger.info("Начинаем поиск курсов с videoLength = 0, null или отсутствующим полем...")
        try {
            val criteria = Criteria().orOperator(
                Criteria.where("videoLength").`is`(0.0),
                Criteria.where("videoLength").`is`(null),
                Criteria.where("videoLength").exists(false)
            )
            val query = Query(criteria)

            query.fields().include("_id")

            val coursesToUpdate = mongoTemplate.find(query, Course::class.java)

            if (coursesToUpdate.isEmpty()) {
                logger.info("Не найдено курсов, требующих обновления длительности видео.")
                return
            }

            logger.info(
                "Найдено {} курсов для обновления длительности видео. Запускаем асинхронные задачи...",
                coursesToUpdate.size
            )

            coursesToUpdate.forEach { course ->
                course.id?.let {
                    updateCourseVideoLengthAsync(it)
                } ?: logger.warn("Обнаружен курс без ID в результатах запроса, пропуск.")
            }

            logger.info("Все асинхронные задачи для обновления длительности видео запущены.")

        } catch (e: Exception) {
            logger.error("Ошибка при поиске курсов для обновления длительности видео: {}", e.message, e)
        }
    }
}