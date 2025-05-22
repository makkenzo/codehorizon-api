package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.dtos.CreateAchievementTriggerDefinitionDTO
import com.makkenzo.codehorizon.dtos.UpdateAchievementTriggerDefinitionDTO
import com.makkenzo.codehorizon.models.AchievementTriggerDefinition
import com.makkenzo.codehorizon.repositories.AchievementTriggerDefinitionRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class AchievementTriggerDefinitionService(
    private val repository: AchievementTriggerDefinitionRepository
    // TODO: Inject AchievementRepository to check for usage before deletion
) {

    fun create(dto: CreateAchievementTriggerDefinitionDTO): AchievementTriggerDefinition {
        if (repository.existsByKey(dto.key)) {
            throw IllegalArgumentException("AchievementTriggerDefinition with key '${dto.key}' already exists.")
        }
        val now = Instant.now()
        val definition = AchievementTriggerDefinition(
            key = dto.key,
            name = dto.name,
            description = dto.description,
            parametersSchema = dto.parametersSchema,
            createdAt = now,
            updatedAt = now
        )
        return repository.save(definition)
    }

    fun getByKey(key: String): AchievementTriggerDefinition? {
        return repository.findByKey(key)
    }

    fun getAll(pageable: Pageable): Page<AchievementTriggerDefinition> {
        return repository.findAll(pageable)
    }

    fun update(key: String, dto: UpdateAchievementTriggerDefinitionDTO): AchievementTriggerDefinition {
        val existingDefinition = repository.findByKey(key)
            ?: throw NoSuchElementException("AchievementTriggerDefinition with key '$key' not found.")

        val updatedDefinition = existingDefinition.copy(
            name = dto.name ?: existingDefinition.name,
            description = dto.description ?: existingDefinition.description,
            parametersSchema = dto.parametersSchema ?: existingDefinition.parametersSchema,
            updatedAt = Instant.now()
        )
        return repository.save(updatedDefinition)
    }

    fun delete(key: String): Boolean {
        val definition = repository.findByKey(key)
        return if (definition != null) {
            // TODO: Before deleting, check if any Achievement entities are currently using this key.
            // If so, deletion might be disallowed or handled differently.
            repository.delete(definition)
            true
        } else {
            false
        }
    }
}
