package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.dtos.CreateAchievementTriggerDefinitionDTO
import com.makkenzo.codehorizon.dtos.UpdateAchievementTriggerDefinitionDTO
import com.makkenzo.codehorizon.models.AchievementTriggerDefinition
import com.makkenzo.codehorizon.repositories.AchievementTriggerDefinitionRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import java.time.Instant
import java.util.*

@ExtendWith(MockitoExtension::class)
class AchievementTriggerDefinitionServiceTest {

    @Mock
    private lateinit var repository: AchievementTriggerDefinitionRepository

    @InjectMocks
    private lateinit var service: AchievementTriggerDefinitionService

    private lateinit var sampleDefinition: AchievementTriggerDefinition
    private lateinit var createDto: CreateAchievementTriggerDefinitionDTO
    private lateinit var updateDto: UpdateAchievementTriggerDefinitionDTO

    @BeforeEach
    fun setUp() {
        val now = Instant.now()
        sampleDefinition = AchievementTriggerDefinition(
            id = "test-id",
            key = "testKey",
            name = "Test Definition",
            description = "A test definition",
            parametersSchema = mapOf("param1" to "string"),
            createdAt = now,
            updatedAt = now
        )

        createDto = CreateAchievementTriggerDefinitionDTO(
            key = "testKey",
            name = "Test Definition",
            description = "A test definition",
            parametersSchema = mapOf("param1" to "string")
        )

        updateDto = UpdateAchievementTriggerDefinitionDTO(
            name = "Updated Name",
            description = "Updated Description",
            parametersSchema = mapOf("param1" to "string", "param2" to "integer")
        )
    }

    @Test
    fun `create should save and return definition when key does not exist`() {
        `when`(repository.existsByKey(createDto.key)).thenReturn(false)
        `when`(repository.save(any(AchievementTriggerDefinition::class.java))).thenReturn(sampleDefinition)

        val result = service.create(createDto)

        assertNotNull(result)
        assertEquals(createDto.key, result.key)
        verify(repository).existsByKey(createDto.key)
        verify(repository).save(any(AchievementTriggerDefinition::class.java))
    }

    @Test
    fun `create should throw IllegalArgumentException when key already exists`() {
        `when`(repository.existsByKey(createDto.key)).thenReturn(true)

        assertThrows(IllegalArgumentException::class.java) {
            service.create(createDto)
        }

        verify(repository).existsByKey(createDto.key)
        verify(repository, never()).save(any(AchievementTriggerDefinition::class.java))
    }

    @Test
    fun `getByKey should return definition when found`() {
        `when`(repository.findByKey("testKey")).thenReturn(sampleDefinition)

        val result = service.getByKey("testKey")

        assertNotNull(result)
        assertEquals("testKey", result?.key)
        verify(repository).findByKey("testKey")
    }

    @Test
    fun `getByKey should return null when not found`() {
        `when`(repository.findByKey("unknownKey")).thenReturn(null)

        val result = service.getByKey("unknownKey")

        assertNull(result)
        verify(repository).findByKey("unknownKey")
    }

    @Test
    fun `getAll should return page of definitions`() {
        val pageable: Pageable = PageRequest.of(0, 10)
        val definitions = listOf(sampleDefinition)
        val page = PageImpl(definitions, pageable, definitions.size.toLong())

        `when`(repository.findAll(pageable)).thenReturn(page)

        val result = service.getAll(pageable)

        assertNotNull(result)
        assertEquals(1, result.content.size)
        assertEquals(sampleDefinition, result.content[0])
        verify(repository).findAll(pageable)
    }

    @Test
    fun `update should update and return definition when found`() {
        `when`(repository.findByKey("testKey")).thenReturn(sampleDefinition)
        `when`(repository.save(any(AchievementTriggerDefinition::class.java))).thenAnswer {
            val def = it.arguments[0] as AchievementTriggerDefinition
            def.copy(name = updateDto.name!!, description = updateDto.description!!, parametersSchema = updateDto.parametersSchema!!, updatedAt = Instant.now())
        }


        val result = service.update("testKey", updateDto)

        assertNotNull(result)
        assertEquals(updateDto.name, result.name)
        assertEquals(updateDto.description, result.description)
        assertEquals(updateDto.parametersSchema, result.parametersSchema)
        verify(repository).findByKey("testKey")
        verify(repository).save(any(AchievementTriggerDefinition::class.java))
    }

    @Test
    fun `update should throw NoSuchElementException when definition not found`() {
        `when`(repository.findByKey("unknownKey")).thenReturn(null)

        assertThrows(NoSuchElementException::class.java) {
            service.update("unknownKey", updateDto)
        }

        verify(repository).findByKey("unknownKey")
        verify(repository, never()).save(any(AchievementTriggerDefinition::class.java))
    }
    
    @Test
    fun `update should use existing values when DTO fields are null`() {
        val partialUpdateDto = UpdateAchievementTriggerDefinitionDTO(name = "Only Name Updated", description = null, parametersSchema = null)
        `when`(repository.findByKey("testKey")).thenReturn(sampleDefinition)
        `when`(repository.save(any(AchievementTriggerDefinition::class.java))).thenAnswer {
            val def = it.arguments[0] as AchievementTriggerDefinition
            def.copy(name = partialUpdateDto.name!!, updatedAt = Instant.now())
        }

        val result = service.update("testKey", partialUpdateDto)

        assertNotNull(result)
        assertEquals(partialUpdateDto.name, result.name)
        assertEquals(sampleDefinition.description, result.description) // Should remain unchanged
        assertEquals(sampleDefinition.parametersSchema, result.parametersSchema) // Should remain unchanged
        verify(repository).save(any(AchievementTriggerDefinition::class.java))
    }


    @Test
    fun `delete should return true and call repository delete when found`() {
        `when`(repository.findByKey("testKey")).thenReturn(sampleDefinition)
        doNothing().`when`(repository).delete(sampleDefinition)

        val result = service.delete("testKey")

        assertTrue(result)
        verify(repository).findByKey("testKey")
        verify(repository).delete(sampleDefinition)
    }

    @Test
    fun `delete should return false when definition not found`() {
        `when`(repository.findByKey("unknownKey")).thenReturn(null)

        val result = service.delete("unknownKey")

        assertFalse(result)
        verify(repository).findByKey("unknownKey")
        verify(repository, never()).delete(any(AchievementTriggerDefinition::class.java))
    }
}
