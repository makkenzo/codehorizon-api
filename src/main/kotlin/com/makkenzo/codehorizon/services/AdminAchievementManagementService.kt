package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.dtos.AdminCreateAchievementDTO
import com.makkenzo.codehorizon.dtos.AdminUpdateAchievementDTO
import com.makkenzo.codehorizon.dtos.PagedResponseDTO
import com.makkenzo.codehorizon.models.Achievement
import com.makkenzo.codehorizon.models.AchievementTriggerDefinition
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
    private val achievementTriggerDefinitionService: AchievementTriggerDefinitionService, // Added injection
    private val mongoTemplate: MongoTemplate
) {

    // TODO: Refactor achievement awarding logic here to use dynamic triggerTypeKey and triggerParameters.
    // This method is a candidate for refactoring as it fetches all achievements,
    // potentially for evaluation against some event.
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

        // Validate TriggerTypeKey and Parameters
        val triggerDefinition = achievementTriggerDefinitionService.getByKey(dto.triggerTypeKey)
            ?: throw IllegalArgumentException("Invalid triggerTypeKey: ${dto.triggerTypeKey}")

        validateTriggerParameters(dto.triggerParameters, triggerDefinition)

        val achievement = Achievement(
            key = dto.key,
            name = dto.name,
            description = dto.description,
            iconUrl = dto.iconUrl,
            triggerTypeKey = dto.triggerTypeKey, // New field
            triggerParameters = dto.triggerParameters, // New field
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
        // TODO: Refactor achievement awarding logic here to use dynamic triggerTypeKey and triggerParameters.
        // If this method is used by a system that evaluates achievements, it will need refactoring.
        return achievementRepository.findAll(pageable)
    }

    // TODO: Refactor achievement awarding logic here to use dynamic triggerTypeKey and triggerParameters.
    // If any logic uses getAchievementById for evaluation, it's part of the awarding process.
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

        
        var triggerTypeKeyToUpdate = existingAchievement.triggerTypeKey
        var triggerParametersToUpdate = existingAchievement.triggerParameters

        if (dto.triggerTypeKey != null) {
            val triggerDefinition = achievementTriggerDefinitionService.getByKey(dto.triggerTypeKey)
                ?: throw IllegalArgumentException("Invalid triggerTypeKey: ${dto.triggerTypeKey}")
            
            triggerTypeKeyToUpdate = dto.triggerTypeKey
            // If triggerTypeKey is changing, parameters must be validated against the new definition.
            // If not provided in DTO, use existing ones IF they are valid for the new type, or clear/default.
            // For simplicity: if key changes, new params must be provided or it's an error if new type expects them.
            // Or, if DTO.triggerParameters is null, we can assume it means "keep old parameters if valid"
            // or "clear parameters". Let's assume for now: if key changes, params must be explicitly set or cleared.
            triggerParametersToUpdate = dto.triggerParameters ?: emptyMap() // Or handle more gracefully
            validateTriggerParameters(triggerParametersToUpdate, triggerDefinition)
        } else if (dto.triggerParameters != null) {
            // triggerTypeKey is not changing, but parameters are. Validate against existing definition.
            val triggerDefinition = achievementTriggerDefinitionService.getByKey(existingAchievement.triggerTypeKey)
                ?: throw IllegalStateException("Existing achievement has an invalid triggerTypeKey: ${existingAchievement.triggerTypeKey}") // Should not happen
            triggerParametersToUpdate = dto.triggerParameters
            validateTriggerParameters(triggerParametersToUpdate, triggerDefinition)
        }

        val updatedAchievement = existingAchievement.copy(
            key = dto.key ?: existingAchievement.key,
            name = dto.name ?: existingAchievement.name,
            description = dto.description ?: existingAchievement.description,
            iconUrl = dto.iconUrl ?: existingAchievement.iconUrl,
            triggerTypeKey = triggerTypeKeyToUpdate, // Updated field
            triggerParameters = triggerParametersToUpdate, // Updated field
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

    private fun validateTriggerParameters(
        parameters: Map<String, Any>,
        definition: AchievementTriggerDefinition
    ) {
        val schema = definition.parametersSchema

        // Check for missing required parameters
        for (requiredParamKey in schema.keys) {
            if (!parameters.containsKey(requiredParamKey)) {
                throw IllegalArgumentException(
                    "Missing required parameter '$requiredParamKey' for trigger type '${definition.key}'."
                )
            }
        }

        // Check for extraneous parameters and basic type compatibility
        for ((paramKey, paramValue) in parameters) {
            if (!schema.containsKey(paramKey)) {
                throw IllegalArgumentException(
                    "Extraneous parameter '$paramKey' provided for trigger type '${definition.key}'. " +
                            "Allowed parameters are: ${schema.keys.joinToString()}."
                )
            }
            // Basic type checking (can be expanded)
            val expectedType = schema[paramKey]?.lowercase()
            when (expectedType) {
                "integer" -> if (paramValue !is Int && !(paramValue is String && paramValue.toIntOrNull() != null) ) {
                    throw IllegalArgumentException("Parameter '$paramKey' for trigger '${definition.key}' must be an integer. Received: $paramValue")
                }
                "string" -> if (paramValue !is String) {
                    throw IllegalArgumentException("Parameter '$paramKey' for trigger '${definition.key}' must be a string. Received: $paramValue")
                }
                "boolean" -> if (paramValue !is Boolean && !(paramValue is String && (paramValue == "true" || paramValue == "false")) ) {
                    throw IllegalArgumentException("Parameter '$paramKey' for trigger '${definition.key}' must be a boolean. Received: $paramValue")
                }
                // Add more types as needed, e.g., "number", "list_string", etc.
                // Note: "Any" in Map<String, Any> makes robust type checking tricky without reflection or custom classes for params.
            }
        }
    }

    // TODO: Refactor achievement awarding logic.
    // Any other methods in this service that are involved in triggering or checking achievement completion
    // will need to be identified and updated. For example, if there's a method like:
    // fun processEvent(event: UserEvent) { ... logic to check achievements ... }
    // That would be a primary candidate for refactoring.
}