package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.events.*
import com.makkenzo.codehorizon.models.Achievement
import com.makkenzo.codehorizon.models.AchievementTriggerType
import com.makkenzo.codehorizon.models.UserAchievement
import com.makkenzo.codehorizon.repositories.*
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Lazy
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AchievementService(
    private val userRepository: UserRepository,
    private val achievementRepository: AchievementRepository,
    private val userAchievementRepository: UserAchievementRepository,
    private val courseProgressRepository: CourseProgressRepository,
    private val reviewRepository: ReviewRepository,
    private val eventPublisher: ApplicationEventPublisher,
    @Lazy private val userService: UserService,
    private val profileRepository: ProfileRepository,
    private val courseRepository: CourseRepository,
) {
    private val logger = LoggerFactory.getLogger(AchievementService::class.java)

    fun getAllDefinitions(): List<Achievement> {
        return achievementRepository.findAll().sortedBy { it.order }
    }

    @Transactional
    fun retroactivelyCheckAndGrantAllAchievementsForAllUsers(specificAchievementKeys: List<String>? = null) {
        logger.info("Начало ретроактивной проверки достижений для всех пользователей (scheduled)...")

        val usersPage = userRepository.findAll(org.springframework.data.domain.PageRequest.of(0, 100))
        var currentPage = usersPage
        var usersProcessedCount = 0

        while (true) {
            currentPage.content.forEach { user ->
                try {
                    logger.debug("Ретроактивная проверка для пользователя: ${user.username} (ID: ${user.id})")

                    val completedCoursesCount =
                        courseProgressRepository.countByUserIdAndProgressGreaterThanEqual(user.id!!, 100.0)
                    checkAndGrantAchievements(
                        user.id!!,
                        AchievementTriggerType.COURSE_COMPLETION_COUNT,
                        completedCoursesCount
                    )
                    if (completedCoursesCount > 0) {
                        checkAndGrantAchievements(user.id!!, AchievementTriggerType.FIRST_COURSE_COMPLETED, 1)
                    }

                    val reviewCount = reviewRepository.countByAuthorId(user.id!!)
                    checkAndGrantAchievements(user.id!!, AchievementTriggerType.REVIEW_COUNT, reviewCount.toInt())
                    if (reviewCount > 0) {
                        checkAndGrantAchievements(user.id!!, AchievementTriggerType.FIRST_REVIEW_WRITTEN, 1)
                    }

                    profileRepository.findByUserId(user.id!!)?.let { profile ->
                        val fieldsToConsider = listOf(
                            profile.avatarUrl,
                            profile.bio,
                            profile.firstName,
                            profile.lastName,
                            profile.location,
                            profile.website
                        )
                        val filledCount = fieldsToConsider.count { !it.isNullOrBlank() }
                        val completionPercentage =
                            if (fieldsToConsider.isNotEmpty()) ((filledCount.toDouble() / fieldsToConsider.size.toDouble()) * 100).toInt() else 0
                        checkAndGrantAchievements(
                            user.id!!,
                            AchievementTriggerType.PROFILE_COMPLETION_PERCENT,
                            completionPercentage
                        )
                    }

                    checkAndGrantAchievements(
                        user.id!!,
                        AchievementTriggerType.DAILY_LOGIN_STREAK,
                        user.dailyLoginStreak
                    )
                    checkAndGrantAchievements(user.id!!, AchievementTriggerType.TOTAL_XP_EARNED, user.xp.toInt())
                    checkAndGrantAchievements(user.id!!, AchievementTriggerType.LEVEL_REACHED, user.level)

                    if (user.roles.any {
                            it.contains("MENTOR", ignoreCase = true) || it.contains(
                                "ADMIN",
                                ignoreCase = true
                            )
                        }) {
                        val createdCourseCount = user.createdCourseIds.size
                        checkAndGrantAchievements(
                            user.id!!,
                            AchievementTriggerType.COURSE_CREATION_COUNT,
                            createdCourseCount
                        )
                    }

                    usersProcessedCount++
                } catch (e: Exception) {
                    logger.error("Ошибка ретроактивной проверки для пользователя ${user.username}: ${e.message}", e)
                }
            }
            if (currentPage.hasNext()) {
                currentPage = userRepository.findAll(currentPage.nextPageable())
            } else {
                break
            }
        }
        logger.info("Ретроактивная проверка достижений (scheduled) завершена. Обработано пользователей: $usersProcessedCount")
    }

    @Transactional
    @Scheduled(cron = "0 0 3 * * ?")
    fun scheduledRetroactiveAchievementCheck() {
        logger.info("Запуск плановой ретроактивной проверки достижений...")
        val users = userRepository.findAll() // TODO: Сделать постранично для больших БД
        var usersProcessedCount = 0
        users.forEach { user ->
            try {
                logger.debug("Ретроактивная проверка для пользователя: {} (ID: {})", user.username, user.id)
                AchievementTriggerType.entries.forEach { triggerType ->
                    if (triggerType == AchievementTriggerType.CATEGORY_COURSES_COMPLETED) {
                        achievementRepository.findByTriggerType(AchievementTriggerType.CATEGORY_COURSES_COMPLETED)
                            .forEach { categoryAchievementDef ->
                                if (categoryAchievementDef.triggerThresholdValue != null) {
                                    val currentValue =
                                        calculateCurrentValueForTrigger(user.id!!, categoryAchievementDef)
                                    checkAndGrantAchievements(
                                        user.id!!,
                                        triggerType,
                                        currentValue,
                                        categoryAchievementDef.triggerThresholdValue
                                    )
                                }
                            }
                    } else if (triggerType != AchievementTriggerType.SPECIFIC_COURSE_COMPLETED &&
                        triggerType != AchievementTriggerType.SPECIFIC_LESSON_COMPLETED
                    ) {
                        val currentValue = calculateCurrentValueForTrigger(
                            user.id!!, Achievement(
                                key = "_retro_check_", name = "", description = "", iconUrl = "",
                                triggerType = triggerType, triggerThreshold = 0
                            )
                        )
                        checkAndGrantAchievements(user.id!!, triggerType, currentValue)
                    }
                }
                usersProcessedCount++
            } catch (e: Exception) {
                logger.error("Ошибка ретроактивной проверки для пользователя ${user.username}: ${e.message}", e)
            }
        }
        logger.info("Ретроактивная проверка достижений завершена. Обработано пользователей: {}", usersProcessedCount)
    }

    @Transactional
    fun checkAndGrantAchievements(
        userId: String,
        triggerType: AchievementTriggerType,
        eventValue: Int? = null,
        relatedEntityId: String? = null
    ) {
        val user = userRepository.findById(userId).orElse(null)
        if (user == null) {
            logger.warn("Пользователь с ID {} не найден при проверке достижений.", userId)
            return
        }

        val relevantAchievements = achievementRepository.findByTriggerType(triggerType)
            .filter { !it.isHidden }

        if (relevantAchievements.isEmpty()) return

        val earnedUserAchievements = userAchievementRepository.findByUserId(userId)
        val earnedAchievementKeys = earnedUserAchievements.map { it.achievementKey }.toSet()

        relevantAchievements.forEach { achievement ->
            if (achievement.key !in earnedAchievementKeys) {
                if (achievement.prerequisites.isNotEmpty()) {
                    val metPrerequisites = achievement.prerequisites.all { prereqKey ->
                        prereqKey in earnedAchievementKeys
                    }
                    if (!metPrerequisites) {
                        return@forEach
                    }
                }

                var grant = false
                val currentEventValue = eventValue ?: calculateCurrentValueForTrigger(userId, achievement)

                if (currentEventValue >= achievement.triggerThreshold) {
                    when (achievement.triggerType) {
                        AchievementTriggerType.SPECIFIC_COURSE_COMPLETED -> {
                            if (relatedEntityId != null && achievement.triggerThresholdValue == relatedEntityId) {
                                grant = true
                            }
                        }

                        AchievementTriggerType.SPECIFIC_LESSON_COMPLETED -> {
                            if (relatedEntityId != null && achievement.triggerThresholdValue == relatedEntityId) {
                                grant = true
                            }
                        }

                        AchievementTriggerType.CATEGORY_COURSES_COMPLETED -> {
                            if (currentEventValue >= achievement.triggerThreshold) {
                                grant = true
                            }
                        }

                        else -> {
                            grant = true
                        }
                    }
                }

                if (grant) {
                    try {
                        userAchievementRepository.save(
                            UserAchievement(
                                userId = userId,
                                achievementKey = achievement.key
                            )
                        )

                        if (achievement.xpBonus > 0) {
                            userService.gainXp(userId, achievement.xpBonus, "Бонус за достижение: ${achievement.name}")
                        }
                        eventPublisher.publishEvent(AchievementUnlockedEvent(this, userId, achievement))
                    } catch (e: Exception) {
                        logger.error(
                            "Ошибка при выдаче достижения {} пользователю {}: {}",
                            achievement.key,
                            userId,
                            e.message
                        )
                    }
                }
            }
        }
    }

    fun getUserAchievementsWithDetails(userId: String): List<Achievement> {
        val userAchievementKeys = userAchievementRepository.findByUserId(userId).map { it.achievementKey }
        if (userAchievementKeys.isEmpty()) {
            return emptyList()
        }
        val achievements = achievementRepository.findByKeyIn(userAchievementKeys).sortedBy { it.order }
        return achievements
    }

    private fun calculateCurrentValueForTrigger(userId: String, achievement: Achievement): Int {
        return when (achievement.triggerType) {
            AchievementTriggerType.COURSE_COMPLETION_COUNT -> courseProgressRepository.countByUserIdAndProgressGreaterThanEqual(
                userId,
                100.0
            )

            AchievementTriggerType.LESSON_COMPLETION_COUNT_TOTAL -> userRepository.findById(userId)
                .orElse(null)?.totalLessonsCompleted?.toInt() ?: 0

            AchievementTriggerType.REVIEW_COUNT -> reviewRepository.countByAuthorId(userId).toInt()
            AchievementTriggerType.PROFILE_COMPLETION_PERCENT -> {
                val profile = profileRepository.findByUserId(userId)
                profile?.let { calculateProfileCompletionPercentage(it) } ?: 0
            }

            AchievementTriggerType.DAILY_LOGIN_STREAK -> userRepository.findById(userId).orElse(null)?.dailyLoginStreak
                ?: 0

            AchievementTriggerType.LESSON_COMPLETION_STREAK_DAILY -> userRepository.findById(userId)
                .orElse(null)?.lessonCompletionStreakDaily ?: 0

            AchievementTriggerType.COURSE_CREATION_COUNT -> userRepository.findById(userId)
                .orElse(null)?.createdCourseIds?.size ?: 0

            AchievementTriggerType.TOTAL_XP_EARNED -> userRepository.findById(userId).orElse(null)?.xp?.toInt() ?: 0
            AchievementTriggerType.LEVEL_REACHED -> userRepository.findById(userId).orElse(null)?.level ?: 0
            AchievementTriggerType.FIRST_COURSE_COMPLETED -> if (courseProgressRepository.countByUserIdAndProgressGreaterThanEqual(
                    userId,
                    100.0
                ) > 0
            ) 1 else 0

            AchievementTriggerType.FIRST_REVIEW_WRITTEN -> if (reviewRepository.countByAuthorId(userId) > 0) 1 else 0
            AchievementTriggerType.CATEGORY_COURSES_COMPLETED -> {
                val categoryName = achievement.triggerThresholdValue ?: return 0
                val completedProgresses =
                    courseProgressRepository.findByUserIdAndProgressGreaterThanEqual(userId, 100.0)
                val courseIds = completedProgresses.map { it.courseId }
                if (courseIds.isEmpty()) return 0
                val courses = courseRepository.findAllById(courseIds)
                courses.count { it.category.equals(categoryName, ignoreCase = true) }
            }
            // Для SPECIFIC_COURSE_COMPLETED и SPECIFIC_LESSON_COMPLETED eventValue и relatedEntityId ОБЯЗАТЕЛЬНЫ при вызове из события.
            // Ретроактивная проверка для них должна быть более специфичной или они должны быть только событийно-управляемыми.
            AchievementTriggerType.SPECIFIC_COURSE_COMPLETED, AchievementTriggerType.SPECIFIC_LESSON_COMPLETED -> 0
        }
    }

    private fun calculateProfileCompletionPercentage(profile: com.makkenzo.codehorizon.models.Profile): Int {
        val fieldsToConsider = listOf(
            profile.avatarUrl,
            profile.bio,
            profile.firstName,
            profile.lastName,
            profile.location,
            profile.website
        )
        val filledCount = fieldsToConsider.count { !it.isNullOrBlank() }
        if (fieldsToConsider.isEmpty()) return 0
        return ((filledCount.toDouble() / fieldsToConsider.size.toDouble()) * 100).toInt()
    }

    @Async
    @EventListener
    fun handleLessonCompleted(event: LessonCompletedEvent) {
        val user = userRepository.findById(event.userId).orElse(null) ?: return

        checkAndGrantAchievements(
            event.userId,
            AchievementTriggerType.LESSON_COMPLETION_COUNT_TOTAL,
            user.totalLessonsCompleted.toInt()
        )
        checkAndGrantAchievements(
            event.userId,
            AchievementTriggerType.LESSON_COMPLETION_STREAK_DAILY,
            user.lessonCompletionStreakDaily
        )

        checkAndGrantAchievements(event.userId, AchievementTriggerType.SPECIFIC_LESSON_COMPLETED, 1, event.lessonId)
    }

    @Async
    @EventListener
    fun handleCourseCompleted(event: CourseCompletedEvent) {
        logger.debug("Обработка CourseCompletedEvent для пользователя {} (курс {})", event.userId, event.courseId)
        val completedCoursesCount =
            courseProgressRepository.countByUserIdAndProgressGreaterThanEqual(event.userId, 100.0)

        checkAndGrantAchievements(event.userId, AchievementTriggerType.COURSE_COMPLETION_COUNT, completedCoursesCount)
        checkAndGrantAchievements(
            event.userId,
            AchievementTriggerType.FIRST_COURSE_COMPLETED,
            if (completedCoursesCount > 0) 1 else 0
        )

        checkAndGrantAchievements(event.userId, AchievementTriggerType.SPECIFIC_COURSE_COMPLETED, 1, event.courseId)

        val course = courseRepository.findById(event.courseId).orElse(null)
        if (course?.category != null) {
            val coursesInCategory = calculateCurrentValueForTrigger(
                event.userId, Achievement(
                    key = "_temp_category_check_",
                    name = "", description = "", iconUrl = "",
                    triggerType = AchievementTriggerType.CATEGORY_COURSES_COMPLETED,
                    triggerThreshold = 0,
                    triggerThresholdValue = course.category
                )
            )
            achievementRepository.findByTriggerType(AchievementTriggerType.CATEGORY_COURSES_COMPLETED)
                .filter { it.triggerThresholdValue.equals(course.category, ignoreCase = true) }
                .forEach { categoryAchievement ->
                    if (coursesInCategory >= categoryAchievement.triggerThreshold) {
                        checkAndGrantAchievements(
                            event.userId,
                            AchievementTriggerType.CATEGORY_COURSES_COMPLETED,
                            coursesInCategory,
                            course.category
                        )
                    }
                }
        }
    }

    @Async
    @EventListener
    fun handleReviewCreated(event: ReviewCreatedEvent) {
        logger.debug("Обработка ReviewCreatedEvent для пользователя {}", event.authorId)
        val reviewCount = reviewRepository.countByAuthorId(event.authorId).toInt()
        checkAndGrantAchievements(event.authorId, AchievementTriggerType.REVIEW_COUNT, reviewCount)
        checkAndGrantAchievements(
            event.authorId,
            AchievementTriggerType.FIRST_REVIEW_WRITTEN,
            if (reviewCount > 0) 1 else 0
        )
    }

    @Async
    @EventListener
    fun handleProfileUpdated(event: ProfileUpdatedEvent) {
        logger.debug(
            "Обработка ProfileUpdatedEvent для пользователя {}, completion: {}",
            event.userId,
            event.completionPercentage
        )
        checkAndGrantAchievements(
            event.userId,
            AchievementTriggerType.PROFILE_COMPLETION_PERCENT,
            event.completionPercentage
        )
    }

    @Async
    @EventListener
    fun handleUserLoggedIn(event: UserLoggedInEvent) {
        logger.debug("Обработка UserLoggedInEvent для пользователя {}", event.userId)
        val user = userRepository.findById(event.userId).orElse(null) ?: return
        checkAndGrantAchievements(event.userId, AchievementTriggerType.DAILY_LOGIN_STREAK, user.dailyLoginStreak)
    }


    @Async
    @EventListener
    fun handleUserLeveledUp(event: UserLeveledUpEvent) {
        logger.debug(
            "Обработка UserLeveledUpEvent для пользователя {}. Новый уровень: {}",
            event.userId,
            event.newLevel
        )
        checkAndGrantAchievements(event.userId, AchievementTriggerType.LEVEL_REACHED, event.newLevel)
    }

    @Async
    @EventListener
    fun handleCourseCreatedByMentor(event: CourseCreatedByMentorEvent) {
        logger.debug("Обработка CourseCreatedByMentorEvent для ментора {}", event.mentorId)
        val mentor = userRepository.findById(event.mentorId).orElse(null) ?: return
        checkAndGrantAchievements(
            event.mentorId,
            AchievementTriggerType.COURSE_CREATION_COUNT,
            mentor.createdCourseIds.size
        )
    }

}