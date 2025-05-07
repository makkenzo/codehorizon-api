//package com.makkenzo.codehorizon.updaters
//
//import com.makkenzo.codehorizon.repositories.CourseRepository
//import com.makkenzo.codehorizon.utils.SlugUtils
//import org.slf4j.LoggerFactory
//import org.springframework.boot.CommandLineRunner
//import org.springframework.stereotype.Component
//import org.springframework.transaction.annotation.Transactional
//
//@Component
//class LessonSlugUpdater(
//    private val courseRepository: CourseRepository
//) : CommandLineRunner {
//
//    private val logger = LoggerFactory.getLogger(LessonSlugUpdater::class.java)
//
//    @Transactional
//    override fun run(vararg args: String?) {
//        logger.info("Запуск LessonSlugUpdater для обновления слагов уроков...")
//        var totalCoursesProcessed = 0
//        var totalLessonsUpdated = 0
//        var totalCoursesSaved = 0
//
//        try {
//            val courses = courseRepository.findAll()
//            logger.info("Найдено {} курсов для проверки слагов уроков.", courses.size)
//
//            courses.forEach { course ->
//                totalCoursesProcessed++
//                var courseNeedsUpdate = false
//                val assignedSlugsInCourse = mutableSetOf<String>()
//
//                course.lessons.forEach { lesson ->
//                    if (!lesson.slug.isNullOrBlank()) {
//                        assignedSlugsInCourse.add(lesson.slug!!)
//                    }
//                }
//
//                course.lessons.forEach { lesson ->
//                    val originalSlug = lesson.slug
//                    var needsUpdate = false
//                    var newSlug = originalSlug
//
//                    if (originalSlug.isNullOrBlank()) {
//                        logger.debug(
//                            "Урок '{}' (ID: {}) в курсе '{}' не имеет слага.",
//                            lesson.title,
//                            lesson.id,
//                            course.title
//                        )
//                        needsUpdate = true
//                    } else if (!assignedSlugsInCourse.add(originalSlug)) {
//                        logger.warn(
//                            "Обнаружен неуникальный слаг '{}' для урока '{}' (ID: {}) в курсе '{}'. Генерируем новый.",
//                            originalSlug, lesson.title, lesson.id, course.title
//                        )
//                        needsUpdate = true
//                        assignedSlugsInCourse.remove(originalSlug)
//                    }
//
//                    if (needsUpdate) {
//                        val baseSlug = SlugUtils.generateSlug(lesson.title)
//                        var uniqueSlugCandidate = baseSlug
//                        var counter = 1
//                        while (!assignedSlugsInCourse.add(uniqueSlugCandidate)) {
//                            uniqueSlugCandidate = "$baseSlug-$counter"
//                            counter++
//                        }
//                        newSlug = uniqueSlugCandidate
//                        lesson.slug = newSlug
//                        totalLessonsUpdated++
//                        courseNeedsUpdate = true
//                        logger.info(
//                            "Уроку '{}' (ID: {}) в курсе '{}' присвоен новый слаг: '{}'",
//                            lesson.title, lesson.id, course.title, newSlug
//                        )
//                    }
//                }
//
//                if (courseNeedsUpdate) {
//                    try {
//                        courseRepository.save(course)
//                        totalCoursesSaved++
//                        logger.debug(
//                            "Курс '{}' (ID: {}) сохранен с обновленными слагами уроков.",
//                            course.title,
//                            course.id
//                        )
//                    } catch (e: Exception) {
//                        logger.error(
//                            "Ошибка при сохранении курса '{}' (ID: {}): {}",
//                            course.title,
//                            course.id,
//                            e.message,
//                            e
//                        )
//                    }
//                }
//
//                if (totalCoursesProcessed % 100 == 0) {
//                    logger.info("Обработано {} курсов...", totalCoursesProcessed)
//                }
//
//            }
//
//            logger.info("Завершено обновление слагов уроков.")
//            logger.info("Всего обработано курсов: {}", totalCoursesProcessed)
//            logger.info("Всего обновлено слагов уроков: {}", totalLessonsUpdated)
//            logger.info("Всего сохранено курсов с изменениями: {}", totalCoursesSaved)
//
//        } catch (e: Exception) {
//            logger.error("Критическая ошибка во время обновления слагов уроков: {}", e.message, e)
//        }
//    }
//}