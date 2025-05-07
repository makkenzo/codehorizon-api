package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.models.Course
import com.makkenzo.codehorizon.repositories.CourseRepository
import com.makkenzo.codehorizon.utils.MediaUtils
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
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
    fun updateLessonVideoLengthAsync(courseId: String, lessonId: String, videoUrl: String) {
        try {
            val lessonVideoDuration = MediaUtils.getVideoDuration(videoUrl)

            val query = Query(Criteria.where("_id").`is`(courseId).and("lessons.id").`is`(lessonId))
            val update = Update().set("lessons.$.videoLength", lessonVideoDuration)

            val result = mongoTemplate.updateFirst(query, update, Course::class.java)

            if (result.modifiedCount > 0) {
                logger.info(
                    "Асинхронно обновлена длительность видео для урока ID {} в курсе ID {}: {}s",
                    lessonId,
                    courseId,
                    lessonVideoDuration
                )

                updateCourseVideoLengthAsync(courseId)
            } else {
                logger.warn(
                    "Урок ID {} в курсе ID {} не найден для обновления длительности видео, или длина не изменилась.",
                    lessonId,
                    courseId
                )
            }
        } catch (e: Exception) {
            logger.error(
                "Критическая ошибка в асинхронном обновлении длительности видео для урока ID {} в курсе {}: {}",
                lessonId,
                courseId,
                e.message,
                e
            )
        }
    }

    @Async
    @Transactional
    fun updateCourseVideoLengthAsync(courseId: String) {
        try {
            val course = courseRepository.findById(courseId).orElse(null)
            if (course == null) {
                logger.warn("Асинхронное обновление длительности: Курс с ID {} не найден.", courseId)
                return
            }

            val totalLessonsLength = course.lessons.sumOf { it.videoLength ?: 0.0 }

            val currentOverallLength = course.videoLength ?: 0.0
            if (currentOverallLength != totalLessonsLength) {
                val updatedCourse = course.copy(videoLength = totalLessonsLength)
                courseRepository.save(updatedCourse)
                logger.info(
                    "Асинхронно обновлена ОБЩАЯ длительность видео для курса '{}' ({}): {}s",
                    course.title,
                    courseId,
                    totalLessonsLength
                )
            } else {
                logger.info(
                    "Общая длительность видео для курса '{}' ({}) не изменилась: {}s",
                    course.title,
                    courseId,
                    totalLessonsLength
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

    @Async
    fun updateMissingLessonVideoLengthsGlobally() {
        logger.info("Запуск глобального обновления длительности видео для уроков...")
        var coursesProcessed = 0
        var lessonsUpdated = 0

        val allCourses = courseRepository.findAll()

        allCourses.forEach { course ->
            var courseWasModified = false
            course.lessons.forEach lessonLoop@{ lesson ->
                if (!lesson.mainAttachment.isNullOrBlank() && (lesson.videoLength == null || lesson.videoLength == 0.0)) {
                    try {
                        logger.info(
                            "Обновление длины для урока ID {} (курс ID {}), URL: {}",
                            lesson.id,
                            course.id,
                            lesson.mainAttachment
                        )
                        val duration = MediaUtils.getVideoDuration(lesson.mainAttachment!!)


                        val query = Query(Criteria.where("_id").`is`(course.id).and("lessons.id").`is`(lesson.id))
                        val update = Update().set("lessons.$.videoLength", duration)
                        val result = mongoTemplate.updateFirst(query, update, Course::class.java)

                        if (result.modifiedCount > 0) {
                            lessonsUpdated++
                            courseWasModified = true
                            logger.info("Успешно обновлена длина урока ID {} до {} сек.", lesson.id, duration)
                        } else {
                            logger.warn(
                                "Не удалось обновить длину урока ID {} в курсе ID {}. Урок не найден или длина не изменилась.",
                                lesson.id,
                                course.id
                            )
                        }
                    } catch (e: Exception) {
                        logger.error(
                            "Ошибка при обновлении длины видео для урока ID {} (курс ID {}): {}",
                            lesson.id, course.id, e.message
                        )

                    }
                }
            }
            if (courseWasModified) {
                try {
                    val updatedCourseForTotalLength = courseRepository.findById(course.id!!).orElse(null)
                    if (updatedCourseForTotalLength != null) {
                        val totalLessonsLength = updatedCourseForTotalLength.lessons.sumOf { it.videoLength ?: 0.0 }
                        if (updatedCourseForTotalLength.videoLength != totalLessonsLength) {
                            val finalCourseUpdate = updatedCourseForTotalLength.copy(videoLength = totalLessonsLength)
                            courseRepository.save(finalCourseUpdate)
                            logger.info(
                                "Обновлена общая длительность видео для курса ID {} до {} сек.",
                                course.id,
                                totalLessonsLength
                            )
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Ошибка при обновлении общей длины курса ID {}: {}", course.id, e.message)
                }
            }
            coursesProcessed++
        }
        logger.info(
            "Глобальное обновление длительности видео для уроков завершено. Обработано курсов: {}. Обновлено уроков: {}.",
            coursesProcessed,
            lessonsUpdated
        )
    }

    fun updateVideoLengthForAllCoursesWithZeroOrNull() {
        logger.info("Начинаем поиск курсов с videoLength = 0, null или отсутствующим полем для ОБНОВЛЕНИЯ ОБЩЕЙ ДЛИНЫ...")
        try {
            val criteria = Criteria().orOperator(
                Criteria.where("videoLength").`is`(0.0),
                Criteria.where("videoLength").`is`(null),
                Criteria.where("videoLength").exists(false)
            )

            val query = Query(criteria)
            query.fields().include("_id", "title")

            val coursesToUpdateDocs = mongoTemplate.find(query, Document::class.java, "courses")

            if (coursesToUpdateDocs.isEmpty()) {
                logger.info("Не найдено курсов, требующих обновления общей длительности видео по критерию 0/null/отсутствует.")
                return
            }

            logger.info(
                "Найдено {} курсов для обновления общей длительности видео. Запускаем асинхронные задачи...",
                coursesToUpdateDocs.size
            )

            coursesToUpdateDocs.forEach { doc ->
                val courseId = doc.getObjectId("_id")?.toString()
                val courseTitle = doc.getString("title") ?: "Без названия"
                if (courseId != null) {
                    logger.info("Запускаем обновление общей длины для курса: '{}' (ID: {})", courseTitle, courseId)
                    updateCourseVideoLengthAsync(courseId)

                    val courseDoc = courseRepository.findById(courseId).orElse(null)
                    courseDoc?.lessons?.forEach { lesson ->
                        if (lesson.videoLength == 0.0 && !lesson.mainAttachment.isNullOrBlank()) {
                            updateLessonVideoLengthAsync(courseId, lesson.id, lesson.mainAttachment!!)
                        }
                    }
                }
            }
            logger.info("Все асинхронные задачи для обновления общей длительности видео запущены.")
        } catch (e: Exception) {
            logger.error("Ошибка при поиске курсов для обновления общей длительности видео: {}", e.message, e)
        }
    }
}