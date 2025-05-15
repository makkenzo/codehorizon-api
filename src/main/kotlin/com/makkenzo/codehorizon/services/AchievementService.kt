package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.events.*
import com.makkenzo.codehorizon.models.*
import com.makkenzo.codehorizon.repositories.*
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Lazy
import org.springframework.context.event.EventListener
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

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
    private val wishlistRepository: WishlistRepository,
) {
    private val logger = LoggerFactory.getLogger(AchievementService::class.java)

    fun getAllDefinitions(): List<Achievement> = achievementRepository.findAll().sortedBy { it.order }

    @Transactional
    fun retroactivelyCheckAndGrantAllAchievementsForAllUsers(specificAchievementKeys: List<String>? = null) {
        logger.info("Начало ретроактивной проверки достижений для всех пользователей (scheduled)...")

        var pageNum = 0
        val pageSize = 100
        var usersPage = userRepository.findAll(PageRequest.of(pageNum, pageSize))
        var usersProcessedCount = 0

        while (usersPage.hasContent()) {
            usersPage.content.forEach { user ->
                try {
                    logger.debug(
                        "Ретроактивная проверка (ручной запуск) для пользователя: {} (ID: {})",
                        user.username,
                        user.id
                    )
                    processUserAchievementsRetroactively(user, specificAchievementKeys)
                    usersProcessedCount++
                } catch (e: Exception) {
                    logger.error(
                        "Ошибка ретроактивной проверки (ручной запуск) для пользователя ${user.username}: ${e.message}",
                        e
                    )
                }
            }
            if (usersPage.hasNext()) {
                pageNum++
                usersPage = userRepository.findAll(PageRequest.of(pageNum, pageSize))
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
        var pageNum = 0
        val pageSize = 100
        var usersPage = userRepository.findAll(PageRequest.of(pageNum, pageSize))
        var usersProcessedCount = 0

        while (usersPage.hasContent()) {
            usersPage.content.forEach { user ->
                try {
                    logger.debug("Ретроактивная проверка для пользователя: {} (ID: {})", user.username, user.id)
                    processUserAchievementsRetroactively(user)
                    usersProcessedCount++
                } catch (e: Exception) {
                    logger.error("Ошибка ретроактивной проверки для пользователя ${user.username}: ${e.message}", e)
                }
            }
            if (usersPage.hasNext()) {
                pageNum++
                usersPage = userRepository.findAll(PageRequest.of(pageNum, pageSize))
            } else {
                break
            }
        }
        logger.info("Ретроактивная проверка достижений завершена. Обработано пользователей: {}", usersProcessedCount)
    }

    private fun processUserAchievementsRetroactively(user: User, specificAchievementKeys: List<String>? = null) {
        val achievementTriggersToCheck = if (specificAchievementKeys.isNullOrEmpty()) {
            AchievementTriggerType.entries
        } else {
            achievementRepository.findByKeyIn(specificAchievementKeys)
                .map { it.triggerType }
                .distinct()
        }

        achievementTriggersToCheck.forEach { triggerType ->
            if (triggerType == AchievementTriggerType.LESSON_COMPLETED_AT_NIGHT ||
                triggerType == AchievementTriggerType.CUSTOM_FRONTEND_EVENT
            ) {
                return@forEach
            }


            if (triggerType == AchievementTriggerType.FIRST_COURSE_COMPLETED_WITHIN_TIMEFRAME) {
                val firstCompletedCourseProgress =
                    courseProgressRepository.findByUserIdAndCompletedAtIsNotNullOrderByCompletedAtAsc(
                        user.id!!,
                        PageRequest.of(0, 1)
                    ).firstOrNull()
                if (firstCompletedCourseProgress?.completedAt != null) {
                    val customData = mapOf("first_course_completion_time" to firstCompletedCourseProgress.completedAt!!)
                    checkAndGrantAchievements(user.id!!, triggerType, 1, customData = customData)
                }
            } else {
                val currentValue = calculateCurrentValueForTrigger(
                    user.id!!,
                    Achievement(
                        key = "_retro_check_",
                        name = "",
                        description = "",
                        iconUrl = "",
                        triggerType = triggerType,
                        triggerThreshold = 0
                    )
                )
                checkAndGrantAchievements(user.id!!, triggerType, currentValue)
            }
        }
    }


    @Transactional
    fun checkAndGrantAchievements(
        userId: String,
        triggerType: AchievementTriggerType,
        eventValue: Int? = null,
        relatedEntityId: String? = null,
        customData: Map<String, Any>? = null
    ) {
        val user = userRepository.findById(userId).orElse(null)
        if (user == null) {
            logger.warn("Пользователь с ID {} не найден при проверке достижений.", userId)
            return
        }

        val relevantAchievements = achievementRepository.findByTriggerType(triggerType)
            .filter { !it.isHidden || (it.isHidden && customData?.get("force_check_hidden_for_key") == it.key) }

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
                val currentEventValue = eventValue ?: calculateCurrentValueForTrigger(userId, achievement, customData)

                if (currentEventValue >= achievement.triggerThreshold) {
                    when (achievement.triggerType) {
                        AchievementTriggerType.SPECIFIC_COURSE_COMPLETED,
                        AchievementTriggerType.SPECIFIC_LESSON_COMPLETED -> {
                            if (relatedEntityId != null && achievement.triggerThresholdValue == relatedEntityId) {
                                grant = true
                            }
                        }

                        AchievementTriggerType.CATEGORY_COURSES_COMPLETED -> {
                            grant = true
                        }

                        AchievementTriggerType.FIRST_COURSE_COMPLETED_WITHIN_TIMEFRAME -> {
                            grant = (currentEventValue == 1)
                        }

                        AchievementTriggerType.LESSON_COMPLETED_AT_NIGHT -> {
                            grant = (currentEventValue == 1)
                        }

                        AchievementTriggerType.MENTOR_STUDENT_COURSE_COMPLETION -> {
                            grant = true
                        }

                        AchievementTriggerType.MENTOR_TOTAL_STUDENT_COMPLETIONS -> {
                            grant = true
                        }

                        AchievementTriggerType.CUSTOM_FRONTEND_EVENT -> {
                            grant = (currentEventValue == 1 && customData?.get("achievement_key") == achievement.key)
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
        if (userAchievementKeys.isEmpty()) return emptyList()
        return achievementRepository.findByKeyIn(userAchievementKeys).sortedBy { it.order }
    }

    private fun calculateCurrentValueForTrigger(
        userId: String,
        achievementDefinition: Achievement,
        customData: Map<String, Any>? = null
    ): Int {
        val user = userRepository.findById(userId).orElse(null) ?: return 0

        return when (achievementDefinition.triggerType) {
            AchievementTriggerType.COURSE_COMPLETION_COUNT -> courseProgressRepository.countByUserIdAndProgressGreaterThanEqual(
                userId,
                100.0
            )

            AchievementTriggerType.LESSON_COMPLETION_COUNT_TOTAL -> user.totalLessonsCompleted.toInt()
            AchievementTriggerType.REVIEW_COUNT -> reviewRepository.countByAuthorId(userId).toInt()
            AchievementTriggerType.PROFILE_COMPLETION_PERCENT -> {
                val profile = profileRepository.findByUserId(userId)
                profile?.let { calculateProfileCompletionPercentage(it) } ?: 0
            }

            AchievementTriggerType.DAILY_LOGIN_STREAK -> user.dailyLoginStreak
            AchievementTriggerType.LESSON_COMPLETION_STREAK_DAILY -> user.lessonCompletionStreakDaily
            AchievementTriggerType.COURSE_CREATION_COUNT -> user.createdCourseIds.size
            AchievementTriggerType.TOTAL_XP_EARNED -> user.xp.toInt()
            AchievementTriggerType.LEVEL_REACHED -> user.level
            AchievementTriggerType.FIRST_COURSE_COMPLETED -> if (courseProgressRepository.countByUserIdAndProgressGreaterThanEqual(
                    userId,
                    100.0
                ) > 0
            ) 1 else 0

            AchievementTriggerType.FIRST_REVIEW_WRITTEN -> if (reviewRepository.countByAuthorId(userId) > 0) 1 else 0
            AchievementTriggerType.CATEGORY_COURSES_COMPLETED -> {
                val categoryName = achievementDefinition.triggerThresholdValue ?: return 0
                val completedProgresses =
                    courseProgressRepository.findByUserIdAndProgressGreaterThanEqual(userId, 100.0)
                val courseIds = completedProgresses.map { it.courseId }
                if (courseIds.isEmpty()) return 0
                val courses = courseRepository.findAllById(courseIds)
                courses.count { it.category.equals(categoryName, ignoreCase = true) }
            }

            AchievementTriggerType.WISHLIST_ITEM_COUNT -> {
                wishlistRepository.countByUserId(userId).toInt()
            }

            AchievementTriggerType.MENTOR_STUDENT_COURSE_COMPLETION -> {
                if (!user.roles.any {
                        it.equals("ROLE_MENTOR", ignoreCase = true) || it.equals(
                            "MENTOR",
                            ignoreCase = true
                        )
                    }) return 0
                var maxCompletionsForSingleCourse = 0
                user.createdCourseIds.forEach { courseId ->
                    val completions =
                        courseProgressRepository.countByCourseIdAndProgressGreaterThanEqual(courseId, 100.0)
                    if (completions > maxCompletionsForSingleCourse) {
                        maxCompletionsForSingleCourse = completions
                    }
                }
                maxCompletionsForSingleCourse
            }

            AchievementTriggerType.MENTOR_TOTAL_STUDENT_COMPLETIONS -> {
                if (!user.roles.any {
                        it.equals("ROLE_MENTOR", ignoreCase = true) || it.equals(
                            "MENTOR",
                            ignoreCase = true
                        )
                    }) return 0
                var totalCompletions = 0L
                user.createdCourseIds.forEach { courseId ->
                    totalCompletions += courseProgressRepository.countByCourseIdAndProgressGreaterThanEqual(
                        courseId,
                        100.0
                    )
                }
                totalCompletions.toInt()
            }

            AchievementTriggerType.CUSTOM_FRONTEND_EVENT -> {
                if (customData?.get("triggered_by_frontend") == true && customData["achievement_key"] == achievementDefinition.key) 1 else 0
            }

            AchievementTriggerType.LESSON_COMPLETED_AT_NIGHT -> {
                val lessonCompletionTime = customData?.get("lesson_completion_time") as? LocalTime
                if (lessonCompletionTime != null) {
                    val nightStart = LocalTime.of(2, 0)
                    val nightEnd = LocalTime.of(5, 0)
                    if (!lessonCompletionTime.isBefore(nightStart) && lessonCompletionTime.isBefore(nightEnd)) 1 else 0
                } else 0
            }

            AchievementTriggerType.FIRST_COURSE_COMPLETED_WITHIN_TIMEFRAME -> {
                val userRegisteredAt = user.createdAt

                val firstCourseCompletionTime = customData?.get("first_course_completion_time") as? Instant
                val timeframeString = achievementDefinition.triggerThresholdValue

                if (firstCourseCompletionTime != null && timeframeString != null) {
                    val duration = parseDurationString(timeframeString)
                    if (duration != null) {
                        if (Duration.between(userRegisteredAt, firstCourseCompletionTime) <= duration) 1 else 0
                    } else 0
                } else 0
            }

            AchievementTriggerType.SPECIFIC_COURSE_COMPLETED,
            AchievementTriggerType.SPECIFIC_LESSON_COMPLETED -> {
                0
            }
        }
    }

    private fun parseDurationString(durationString: String): Duration? {
        return when {
            durationString.endsWith("h") -> Duration.ofHours(durationString.removeSuffix("h").toLongOrNull() ?: 0)
            durationString.endsWith("d") -> Duration.ofDays(durationString.removeSuffix("d").toLongOrNull() ?: 0)
            durationString.endsWith("m") -> Duration.ofMinutes(durationString.removeSuffix("m").toLongOrNull() ?: 0)
            else -> null
        }
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

        val completionTime = LocalTime.now(ZoneOffset.UTC)
        val customDataNight = mapOf("lesson_completion_time" to completionTime)
        checkAndGrantAchievements(
            event.userId,
            AchievementTriggerType.LESSON_COMPLETED_AT_NIGHT,
            1,
            customData = customDataNight
        )
    }

    @Async
    @EventListener
    fun handleCourseCompleted(event: CourseCompletedEvent) {
        logger.debug(
            "Обработка CourseCompletedEvent для пользователя {} (курс {}), завершен в {}",
            event.userId,
            event.courseId,
            event.completedAt
        )
        val user = userRepository.findById(event.userId).orElse(null) ?: return

        val completedCoursesCount =
            courseProgressRepository.countByUserIdAndProgressGreaterThanEqual(event.userId, 100.0)
        checkAndGrantAchievements(event.userId, AchievementTriggerType.COURSE_COMPLETION_COUNT, completedCoursesCount)

        if (completedCoursesCount == 1) {
            checkAndGrantAchievements(event.userId, AchievementTriggerType.FIRST_COURSE_COMPLETED, 1)

            val customDataTimeframe = mapOf("first_course_completion_time" to event.completedAt)
            checkAndGrantAchievements(
                event.userId,
                AchievementTriggerType.FIRST_COURSE_COMPLETED_WITHIN_TIMEFRAME,
                1,
                customData = customDataTimeframe
            )
        }

        checkAndGrantAchievements(event.userId, AchievementTriggerType.SPECIFIC_COURSE_COMPLETED, 1, event.courseId)

        val course = courseRepository.findById(event.courseId).orElse(null)
        if (course?.category != null) {
            val coursesInCategory = calculateCurrentValueForTrigger(
                user.id!!,
                Achievement(
                    key = "_temp_",
                    name = "",
                    description = "",
                    iconUrl = "",
                    triggerType = AchievementTriggerType.CATEGORY_COURSES_COMPLETED,
                    triggerThreshold = 0,
                    triggerThresholdValue = course.category
                )
            )
            checkAndGrantAchievements(
                user.id!!,
                AchievementTriggerType.CATEGORY_COURSES_COMPLETED,
                coursesInCategory,
                customData = mapOf("category" to course.category)
            )
        }

        val completedCourse = course
        if (completedCourse != null && completedCourse.authorId != event.userId) {
            val mentor = userRepository.findById(completedCourse.authorId).orElse(null)
            if (mentor != null && (mentor.roles.contains("ROLE_MENTOR") || mentor.roles.contains("MENTOR"))) {
                val mentorId = mentor.id!!
                val completionsForThisMentorCourse =
                    courseProgressRepository.countByCourseIdAndProgressGreaterThanEqual(completedCourse.id!!, 100.0)
                checkAndGrantAchievements(
                    mentorId,
                    AchievementTriggerType.MENTOR_STUDENT_COURSE_COMPLETION,
                    completionsForThisMentorCourse
                )

                var totalCompletionsForMentor = 0L
                mentor.createdCourseIds.forEach { mentorCourseId ->
                    totalCompletionsForMentor += courseProgressRepository.countByCourseIdAndProgressGreaterThanEqual(
                        mentorCourseId,
                        100.0
                    )
                }
                checkAndGrantAchievements(
                    mentorId,
                    AchievementTriggerType.MENTOR_TOTAL_STUDENT_COMPLETIONS,
                    totalCompletionsForMentor.toInt()
                )
            }
        }
    }

    @Async
    @EventListener
    fun handleReviewCreated(event: ReviewCreatedEvent) {
        logger.debug("Обработка ReviewCreatedEvent для пользователя {}", event.authorId)
        val reviewCount = reviewRepository.countByAuthorId(event.authorId).toInt()
        checkAndGrantAchievements(event.authorId, AchievementTriggerType.REVIEW_COUNT, reviewCount)
        if (reviewCount == 1) {
            checkAndGrantAchievements(event.authorId, AchievementTriggerType.FIRST_REVIEW_WRITTEN, 1)
        }
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

    @Transactional
    fun grantAchievementForCustomFrontendEvent(userId: String, achievementKey: String) {
        val achievement = achievementRepository.findByKey(achievementKey)
        if (achievement != null && achievement.triggerType == AchievementTriggerType.CUSTOM_FRONTEND_EVENT && achievement.isHidden) {
            if (!userAchievementRepository.existsByUserIdAndAchievementKey(userId, achievementKey)) {
                checkAndGrantAchievements(
                    userId,
                    AchievementTriggerType.CUSTOM_FRONTEND_EVENT,
                    1,
                    customData = mapOf(
                        "triggered_by_frontend" to true,
                        "achievement_key" to achievementKey,
                        "force_check_hidden_for_key" to achievementKey
                    )
                )
            }
        } else {
            logger.warn(
                "Попытка выдать некорректное достижение {} через кастомное событие для пользователя {}",
                achievementKey,
                userId
            )
        }
    }

    private fun calculateProfileCompletionPercentage(profile: Profile): Int {
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
}