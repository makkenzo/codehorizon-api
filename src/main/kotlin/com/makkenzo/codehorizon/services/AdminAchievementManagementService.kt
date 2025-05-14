package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.models.Achievement
import com.makkenzo.codehorizon.repositories.AchievementRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AdminAchievementManagementService(private val achievementRepository: AchievementRepository) {
    @Cacheable("allAchievementsDefinitions")
    fun getAllAchievements(): List<Achievement> = achievementRepository.findAll().sortedBy { it.order }

    fun getAchievementById(id: String): Achievement? = achievementRepository.findById(id).orElse(null)

    @Transactional
    @CacheEvict("allAchievementsDefinitions", allEntries = true)
    fun createAchievement(achievement: Achievement): Achievement {
        if (achievementRepository.findByKey(achievement.key) != null) {
            throw IllegalArgumentException("Достижение с ключом '${achievement.key}' уже существует.")
        }

        return achievementRepository.save(achievement)
    }

    @Transactional
    @CacheEvict("allAchievementsDefinitions", allEntries = true)
    fun updateAchievement(id: String, achievementDetails: Achievement): Achievement? {
        val existingAchievement = achievementRepository.findById(id).orElse(null) ?: return null

        if (existingAchievement.key != achievementDetails.key && achievementRepository.findByKey(achievementDetails.key) != null) {
            throw IllegalArgumentException("Достижение с ключом '${achievementDetails.key}' уже занято другим достижением.")
        }

        val updatedAchievement = existingAchievement.copy(
            key = achievementDetails.key,
            name = achievementDetails.name,
            description = achievementDetails.description,
            iconUrl = achievementDetails.iconUrl,
            triggerType = achievementDetails.triggerType,
            triggerThreshold = achievementDetails.triggerThreshold,
            xpBonus = achievementDetails.xpBonus,
            isGlobal = achievementDetails.isGlobal,
            order = achievementDetails.order
        )
        return achievementRepository.save(updatedAchievement)
    }

    @Transactional
    @CacheEvict("allAchievementsDefinitions", allEntries = true)
    fun deleteAchievement(id: String): Boolean {
        return if (achievementRepository.existsById(id)) {
            achievementRepository.deleteById(id)
            true
        } else {
            false
        }
    }
}