package com.makkenzo.codehorizon.updaters

import com.makkenzo.codehorizon.repositories.CourseRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Component
class LessonIdUpdater(
    private val courseRepository: CourseRepository
) : CommandLineRunner {

    private val logger = LoggerFactory.getLogger(LessonIdUpdater::class.java)

    @Transactional
    override fun run(vararg args: String?) {
        logger.info("--- Запуск LessonIdUpdater для генерации ID уроков ---")
        var totalCoursesProcessed = 0
        var totalLessonsChecked = 0
        var totalLessonsUpdated = 0
        var totalCoursesSaved = 0
        val coursesWithErrors = mutableListOf<String>()

        try {
            val courses = courseRepository.findAll()
            logger.info("Найдено {} курсов для проверки ID уроков.", courses.size)

            courses.forEach { course ->
                totalCoursesProcessed++
                var courseNeedsUpdate = false

                if (course.lessons.isNullOrEmpty()) {

                    if (totalCoursesProcessed % 100 == 0) {
                        logger.info("Обработано {} курсов...", totalCoursesProcessed)
                    }
                    return@forEach
                }

                course.lessons.forEach { lesson ->
                    totalLessonsChecked++
                    if (lesson.id.isNullOrBlank()) {
                        val newId = UUID.randomUUID().toString()
                        logger.warn(
                            "Урок '{}' в курсе '{}' (ID курса: {}) не имеет ID. Генерируем новый: {}",
                            lesson.title,
                            course.title,
                            course.id ?: "N/A",
                            newId
                        )
                        courseNeedsUpdate = true
                    }
                }

                if (courseNeedsUpdate) {
                    course.lessons.forEachIndexed { index, lesson ->
                        if (lesson.id.isNullOrBlank()) {
                            val newId = UUID.randomUUID().toString()
                            course.lessons[index] = lesson.copy(id = newId)
                            totalLessonsUpdated++
                        }
                    }

                    try {
                        courseRepository.save(course)
                        totalCoursesSaved++
                        logger.debug(
                            "Курс '{}' (ID: {}) сохранен с обновленными ID уроков.",
                            course.title,
                            course.id ?: "N/A"
                        )
                    } catch (e: Exception) {
                        logger.error(
                            "Ошибка при сохранении курса '{}' (ID: {}): {}",
                            course.title,
                            course.id ?: "N/A",
                            e.message,
                            e
                        )
                        course.id?.let { coursesWithErrors.add(it) }
                    }
                }

                if (totalCoursesProcessed % 100 == 0) {
                    logger.info("Обработано {} курсов...", totalCoursesProcessed)
                }
            }

            logger.info("--- Завершено обновление ID уроков ---")
            logger.info("Всего обработано курсов: {}", totalCoursesProcessed)
            logger.info("Всего проверено уроков: {}", totalLessonsChecked)
            logger.info("Всего обновлено ID уроков: {}", totalLessonsUpdated)
            logger.info("Всего сохранено курсов с изменениями: {}", totalCoursesSaved)
            if (coursesWithErrors.isNotEmpty()) {
                logger.warn("Курсы, при сохранении которых возникли ошибки: {}", coursesWithErrors.joinToString())
            }

        } catch (e: Exception) {
            logger.error("Критическая ошибка во время обновления ID уроков: {}", e.message, e)
        }
    }
}