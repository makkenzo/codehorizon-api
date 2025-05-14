package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.events.AchievementUnlockedEvent
import com.makkenzo.codehorizon.models.Achievement
import com.makkenzo.codehorizon.models.AchievementTriggerType
import com.makkenzo.codehorizon.models.UserAchievement
import com.makkenzo.codehorizon.repositories.*
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Lazy
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
) {
    private val logger = LoggerFactory.getLogger(AchievementService::class.java)

    @Transactional
    fun checkAndGrantAchievements(userId: String, triggerType: AchievementTriggerType, eventValue: Int? = null) {
        val user = userRepository.findById(userId).orElse(null) ?: return

        val relevantAchievements = achievementRepository.findByTriggerType(triggerType)
        if (relevantAchievements.isEmpty()) return

        val earnedAchievementKeys = userAchievementRepository.findByUserId(userId).map { it.achievementKey }

        relevantAchievements.forEach { achievement ->
            if (achievement.key !in earnedAchievementKeys) {
                var grant = false
                when (achievement.triggerType) {
                    AchievementTriggerType.COURSE_COMPLETION_COUNT -> {
                        val completedCount =
                            courseProgressRepository.countByUserIdAndProgressGreaterThanEqual(userId, 100.0)
                        if (completedCount >= achievement.triggerThreshold) grant = true
                    }

                    AchievementTriggerType.FIRST_COURSE_COMPLETED -> {
                        val completedCount =
                            courseProgressRepository.countByUserIdAndProgressGreaterThanEqual(userId, 100.0)
                        if (completedCount >= 1 && achievement.triggerThreshold == 1) grant = true
                    }

                    AchievementTriggerType.REVIEW_COUNT -> {
                        val reviewCount = reviewRepository.countByAuthorId(userId)
                        if (reviewCount >= achievement.triggerThreshold.toLong()) grant = true
                    }

                    AchievementTriggerType.FIRST_REVIEW_WRITTEN -> {
                        val reviewCount = reviewRepository.countByAuthorId(userId)
                        if (reviewCount >= 1 && achievement.triggerThreshold == 1) grant = true
                    }

                    AchievementTriggerType.LEVEL_REACHED -> {
                        if (user.level >= achievement.triggerThreshold) grant = true
                    }

                    AchievementTriggerType.TOTAL_XP_EARNED -> {
                        if (user.xp >= achievement.triggerThreshold.toLong()) grant = true
                    }

                    AchievementTriggerType.DAILY_LOGIN_STREAK -> {
                        if (user.dailyStreak >= achievement.triggerThreshold) grant = true
                    }

                    AchievementTriggerType.LESSON_COMPLETION_STREAK -> {
                        if (eventValue != null && eventValue >= achievement.triggerThreshold) {
                            grant = true
                        }
                    }

                    AchievementTriggerType.PROFILE_COMPLETION_PERCENT -> {
                        if (eventValue != null && eventValue >= achievement.triggerThreshold) {
                            grant = true
                        }
                    }

                    AchievementTriggerType.COURSE_CREATION_COUNT -> {
                        if (eventValue != null && eventValue >= achievement.triggerThreshold) {
                            grant = true
                        }
                    }
                }

                if (grant) {
                    userAchievementRepository.save(UserAchievement(userId = userId, achievementKey = achievement.key))
                    logger.info("User $userId earned achievement: ${achievement.name}")
                    if (achievement.xpBonus > 0) {
                        userService.gainXp(userId, achievement.xpBonus, "achievement bonus: ${achievement.name}")
                    }

                    try {
                        eventPublisher.publishEvent(AchievementUnlockedEvent(this, userId, achievement))
                        logger.info("Опубликовано событие AchievementUnlockedEvent для пользователя $userId, достижение: ${achievement.name}")
                    } catch (e: Exception) {
                        logger.error(
                            "Ошибка публикации AchievementUnlockedEvent для пользователя $userId, достижение ${achievement.name}: ${e.message}",
                            e
                        )
                    }
                }
            }
        }
    }

    fun getUserAchievementsWithDetails(userId: String): List<Achievement> {
        val userAchievementKeys = userAchievementRepository.findByUserId(userId).map { it.achievementKey }
        if (userAchievementKeys.isEmpty()) return emptyList()
        return achievementRepository.findAllById(userAchievementKeys).sortedBy { it.order }
    }
}