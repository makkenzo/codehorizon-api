package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.dtos.GlobalAchievementDTO
import com.makkenzo.codehorizon.dtos.PagedResponseDTO
import com.makkenzo.codehorizon.models.Achievement
import com.makkenzo.codehorizon.repositories.UserAchievementRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.TextCriteria
import org.springframework.stereotype.Service

@Service
class GlobalAchievementService(
    private val mongoTemplate: MongoTemplate,
    private val userAchievementRepository: UserAchievementRepository
) {
    private val logger = LoggerFactory.getLogger(GlobalAchievementService::class.java)

    fun getAllDefinitions(
        pageable: Pageable,
        userId: String?,
        statusFilter: String?,
        categoryFilter: String?,
        searchQuery: String?
    ): PagedResponseDTO<GlobalAchievementDTO> {
        logger.debug(
            "Fetching achievement definitions for userId: {}, statusFilter: {}, categoryFilter: {}, searchQuery: {}, pageable: {}",
            userId, statusFilter, categoryFilter, searchQuery, pageable
        )

        val criteria = Criteria()
        criteria.and("isGlobal").`is`(true)

        categoryFilter?.let {
            if (it.isNotBlank() && it.lowercase() != "all") {
                criteria.and("category").`is`(it)
            }
        }

        val textCriteria = searchQuery?.let {
            if (it.trim().length >= 2) {
                TextCriteria.forDefaultLanguage().matchingPhrase(it.trim())
            } else null
        }

        val query = Query(criteria)
        textCriteria?.let { query.addCriteria(it) }
        query.with(pageable)

        val totalElementsQuery = Query(criteria)
        textCriteria?.let { totalElementsQuery.addCriteria(it) }
        val totalElements = mongoTemplate.count(totalElementsQuery, Achievement::class.java)

        val definitions: List<Achievement> = mongoTemplate.find(query, Achievement::class.java)
        logger.debug("Found {} achievement definitions from DB matching criteria.", definitions.size)


        val userEarnedKeys: Set<String> = userId?.let {
            userAchievementRepository.findByUserId(it).map { ua -> ua.achievementKey }.toSet()
        } ?: emptySet()

        logger.debug("User {} has earned achievement keys: {}", userId ?: "anonymous", userEarnedKeys)

        val contentDTOs = definitions.mapNotNull { definition ->
            val isEarned = userEarnedKeys.contains(definition.key)
            val earnedAtInstant = if (isEarned) {
                userAchievementRepository.findByUserId(userId!!).find { it.achievementKey == definition.key }?.earnedAt
            } else null


            if (userId != null && statusFilter != null && statusFilter != "all") {
                when (statusFilter) {
                    "earned" -> if (!isEarned) return@mapNotNull null
                    "unearned" -> if (isEarned) return@mapNotNull null
                }
            }

            GlobalAchievementDTO(
                id = definition.id!!,
                key = definition.key,
                name = definition.name,
                description = definition.description,
                iconUrl = definition.iconUrl,
                xpBonus = definition.xpBonus,
                order = definition.order,
                category = definition.category,
                isEarnedByUser = isEarned,
                earnedAt = earnedAtInstant
            )
        }
        logger.debug("Mapped {} DTOs after status filter.", contentDTOs.size)

        val page: Page<GlobalAchievementDTO> = PageImpl(contentDTOs, pageable, totalElements)

        return PagedResponseDTO(
            content = page.content,
            pageNumber = page.number,
            pageSize = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            isLast = page.isLast
        )
    }
}