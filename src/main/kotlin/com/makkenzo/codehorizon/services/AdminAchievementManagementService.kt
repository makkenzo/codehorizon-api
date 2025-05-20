package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.dtos.AdminCreateAchievementDTO
import com.makkenzo.codehorizon.dtos.AdminUpdateAchievementDTO
import com.makkenzo.codehorizon.dtos.PagedResponseDTO
import com.makkenzo.codehorizon.models.Achievement
import com.makkenzo.codehorizon.repositories.AchievementRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AdminAchievementManagementService(
    private val achievementRepository: AchievementRepository,
    private val mongoTemplate: MongoTemplate
) {
    @Cacheable("allAchievementsList")
    fun getAllAchievementsList(): List<Achievement> = achievementRepository.findAll().sortedBy { it.order }

    @Cacheable(
        "allAchievementsPagedDTO",
        key = "#pageable.pageNumber + '-' + #pageable.pageSize + '-' + #pageable.sort.toString()"
    )
    fun getAllAchievementsPagedDTO(pageable: Pageable): PagedResponseDTO<Achievement> {
        val page: Page<Achievement> = achievementRepository.findAll(pageable)
        return PagedResponseDTO(
            content = page.content,
            pageNumber = page.number,
            pageSize = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            isLast = page.isLast
        )
    }

    @CacheEvict(
        value = ["allAchievementsList", "allAchievementsPagedDTO", "allAchievementsPaged", "achievementCategories"],
        allEntries = true
    )
    @Transactional
    fun createAchievement(dto: AdminCreateAchievementDTO): Achievement {
        if (achievementRepository.findByKey(dto.key) != null) {
            throw IllegalArgumentException("Достижение с ключом '${dto.key}' уже существует.")
        }

        val achievement = Achievement(
            key = dto.key,
            name = dto.name,
            description = dto.description,
            iconUrl = dto.iconUrl,
            triggerType = dto.triggerType,
            triggerThreshold = dto.triggerThreshold,
            triggerThresholdValue = dto.triggerThresholdValue,
            xpBonus = dto.xpBonus,
            rarity = dto.rarity,
            isGlobal = dto.isGlobal,
            order = dto.order,
            category = dto.category?.trim()?.ifBlank { null },
            isHidden = dto.isHidden,
            prerequisites = dto.prerequisites.filter { it.isNotBlank() }.distinct()
        )

        return achievementRepository.save(achievement)
    }

    @Cacheable("allAchievementsPaged", key = "#pageable.pageNumber + '-' + #pageable.pageSize + '-' + #pageable.sort")
    fun getAllAchievements(pageable: Pageable): Page<Achievement> {
        return achievementRepository.findAll(pageable)
    }

    fun getAchievementById(id: String): Achievement? = achievementRepository.findById(id).orElse(null)

    @Transactional
    @CacheEvict(
        value = ["allAchievementsList", "allAchievementsPagedDTO", "allAchievementsPaged", "achievementCategories"],
        allEntries = true
    )
    fun updateAchievement(id: String, dto: AdminUpdateAchievementDTO): Achievement {
        val existingAchievement = achievementRepository.findById(id)
            .orElseThrow { NoSuchElementException("Достижение с ID '$id' не найдено.") }

        dto.key?.let { newKey ->
            if (newKey != existingAchievement.key && achievementRepository.findByKey(newKey) != null) {
                throw IllegalArgumentException("Достижение с ключом '$newKey' уже занято другим достижением.")
            }
        }

        val updatedAchievement = existingAchievement.copy(
            key = dto.key ?: existingAchievement.key,
            name = dto.name ?: existingAchievement.name,
            description = dto.description ?: existingAchievement.description,
            iconUrl = dto.iconUrl ?: existingAchievement.iconUrl,
            triggerType = dto.triggerType ?: existingAchievement.triggerType,
            triggerThreshold = dto.triggerThreshold ?: existingAchievement.triggerThreshold,
            triggerThresholdValue = dto.triggerThresholdValue ?: existingAchievement.triggerThresholdValue,
            xpBonus = dto.xpBonus ?: existingAchievement.xpBonus,
            rarity = dto.rarity ?: existingAchievement.rarity,
            isGlobal = dto.isGlobal ?: existingAchievement.isGlobal,
            order = dto.order ?: existingAchievement.order,
            category = dto.category?.trim()?.ifBlank { null } ?: existingAchievement.category,
            isHidden = dto.isHidden ?: existingAchievement.isHidden,
            prerequisites = dto.prerequisites?.filter { it.isNotBlank() }?.distinct()
                ?: existingAchievement.prerequisites
        )

        return achievementRepository.save(updatedAchievement)
    }

    @CacheEvict(
        value = ["allAchievementsList", "allAchievementsPagedDTO", "allAchievementsPaged", "achievementCategories"],
        allEntries = true
    )
    @Transactional
    fun deleteAchievement(id: String): Boolean {
        return if (achievementRepository.existsById(id)) {
            achievementRepository.deleteById(id)
            true
        } else {
            false
        }
    }

    @Cacheable("achievementCategories")
    fun getDistinctAchievementCategories(): List<String> {
        val query = Query().addCriteria(Criteria.where("category").ne(null).ne(""))
        val categories = mongoTemplate.findDistinct(query, "category", "achievements", String::class.java)
        return categories.sorted()
    }
}