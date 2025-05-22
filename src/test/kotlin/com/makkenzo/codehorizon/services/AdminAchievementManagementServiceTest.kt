package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.dtos.AdminCreateAchievementDTO
import com.makkenzo.codehorizon.dtos.AdminUpdateAchievementDTO
import com.makkenzo.codehorizon.models.Achievement
import com.makkenzo.codehorizon.models.AchievementRarity
import com.makkenzo.codehorizon.models.AchievementTriggerDefinition
import com.makkenzo.codehorizon.repositories.AchievementRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.mongodb.core.MongoTemplate
import java.time.Instant
import java.util.*

@ExtendWith(MockitoExtension::class)
class AdminAchievementManagementServiceTest {

    @Mock
    private lateinit var achievementRepository: AchievementRepository

    @Mock
    private lateinit var achievementTriggerDefinitionService: AchievementTriggerDefinitionService

    @Mock
    private lateinit var mongoTemplate: MongoTemplate // Mocked but not used in these specific tests

    @InjectMocks
    private lateinit var adminAchievementManagementService: AdminAchievementManagementService

    private lateinit var sampleTriggerDefinition: AchievementTriggerDefinition
    private lateinit var createDto: AdminCreateAchievementDTO
    private lateinit var existingAchievement: Achievement

    @BeforeEach
    fun setUp() {
        sampleTriggerDefinition = AchievementTriggerDefinition(
            id = "trigger-def-id",
            key = "COURSE_COMPLETION_COUNT",
            name = "Course Completion Count",
            description = "Complete N courses",
            parametersSchema = mapOf("threshold" to "integer"),
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        createDto = AdminCreateAchievementDTO(
            key = "achKey",
            name = "Test Achievement",
            description = "Test Desc",
            iconUrl = "http://example.com/icon.png",
            triggerTypeKey = "COURSE_COMPLETION_COUNT",
            triggerParameters = mapOf("threshold" to 5),
            xpBonus = 100L,
            rarity = AchievementRarity.COMMON,
            isGlobal = true,
            order = 1,
            category = "Testing",
            isHidden = false,
            prerequisites = emptyList()
        )

        existingAchievement = Achievement(
            id = "existing-ach-id",
            key = "existingKey",
            name = "Existing Achievement",
            description = "Existing Desc",
            iconUrl = "http://example.com/existing.png",
            triggerTypeKey = "COURSE_COMPLETION_COUNT",
            triggerParameters = mapOf("threshold" to 3),
            xpBonus = 50L,
            rarity = AchievementRarity.RARE
        )
    }

    // --- createAchievement Tests ---

    @Test
    fun `createAchievement should succeed with valid triggerTypeKey and parameters`() {
        `when`(achievementRepository.findByKey(createDto.key)).thenReturn(null)
        `when`(achievementTriggerDefinitionService.getByKey(createDto.triggerTypeKey)).thenReturn(sampleTriggerDefinition)
        `when`(achievementRepository.save(any(Achievement::class.java))).thenAnswer { it.arguments[0] }

        val result = adminAchievementManagementService.createAchievement(createDto)

        assertNotNull(result)
        assertEquals(createDto.key, result.key)
        assertEquals(createDto.triggerTypeKey, result.triggerTypeKey)
        assertEquals(createDto.triggerParameters, result.triggerParameters)
        verify(achievementTriggerDefinitionService).getByKey(createDto.triggerTypeKey)
        verify(achievementRepository).save(any(Achievement::class.java))
    }

    @Test
    fun `createAchievement should throw IllegalArgumentException for invalid triggerTypeKey`() {
        `when`(achievementRepository.findByKey(createDto.key)).thenReturn(null)
        `when`(achievementTriggerDefinitionService.getByKey(createDto.triggerTypeKey)).thenReturn(null)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            adminAchievementManagementService.createAchievement(createDto)
        }
        assertTrue(exception.message!!.contains("Invalid triggerTypeKey"))
        verify(achievementRepository, never()).save(any(Achievement::class.java))
    }

    @Test
    fun `createAchievement should throw IllegalArgumentException for missing required parameter`() {
        val dtoWithMissingParam = createDto.copy(triggerParameters = emptyMap()) // 'threshold' is missing
        `when`(achievementRepository.findByKey(dtoWithMissingParam.key)).thenReturn(null)
        `when`(achievementTriggerDefinitionService.getByKey(dtoWithMissingParam.triggerTypeKey)).thenReturn(sampleTriggerDefinition)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            adminAchievementManagementService.createAchievement(dtoWithMissingParam)
        }
        assertTrue(exception.message!!.contains("Missing required parameter 'threshold'"))
        verify(achievementRepository, never()).save(any(Achievement::class.java))
    }

    @Test
    fun `createAchievement should throw IllegalArgumentException for extraneous parameter`() {
        val dtoWithExtraneousParam = createDto.copy(triggerParameters = mapOf("threshold" to 5, "extra" to "value"))
        `when`(achievementRepository.findByKey(dtoWithExtraneousParam.key)).thenReturn(null)
        `when`(achievementTriggerDefinitionService.getByKey(dtoWithExtraneousParam.triggerTypeKey)).thenReturn(sampleTriggerDefinition)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            adminAchievementManagementService.createAchievement(dtoWithExtraneousParam)
        }
        assertTrue(exception.message!!.contains("Extraneous parameter 'extra'"))
        verify(achievementRepository, never()).save(any(Achievement::class.java))
    }

    @Test
    fun `createAchievement should throw IllegalArgumentException for parameter type mismatch`() {
        val dtoWithTypeMismatch = createDto.copy(triggerParameters = mapOf("threshold" to "not-an-integer"))
        `when`(achievementRepository.findByKey(dtoWithTypeMismatch.key)).thenReturn(null)
        `when`(achievementTriggerDefinitionService.getByKey(dtoWithTypeMismatch.triggerTypeKey)).thenReturn(sampleTriggerDefinition)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            adminAchievementManagementService.createAchievement(dtoWithTypeMismatch)
        }
        assertTrue(exception.message!!.contains("must be an integer"))
        verify(achievementRepository, never()).save(any(Achievement::class.java))
    }
    
    @Test
    fun `createAchievement should throw IllegalArgumentException if achievement key already exists`() {
        `when`(achievementRepository.findByKey(createDto.key)).thenReturn(existingAchievement)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            adminAchievementManagementService.createAchievement(createDto)
        }
        assertTrue(exception.message!!.contains("Достижение с ключом '${createDto.key}' уже существует."))
        verify(achievementTriggerDefinitionService, never()).getByKey(anyString())
        verify(achievementRepository, never()).save(any(Achievement::class.java))
    }

    // --- updateAchievement Tests ---

    @Test
    fun `updateAchievement should succeed with valid triggerTypeKey and parameters`() {
        val updateDto = AdminUpdateAchievementDTO(
            triggerTypeKey = "NEW_TRIGGER_TYPE",
            triggerParameters = mapOf("newParam" to "value")
        )
        val newTriggerDef = sampleTriggerDefinition.copy(key = "NEW_TRIGGER_TYPE", parametersSchema = mapOf("newParam" to "string"))

        `when`(achievementRepository.findById(existingAchievement.id!!)).thenReturn(Optional.of(existingAchievement))
        `when`(achievementTriggerDefinitionService.getByKey("NEW_TRIGGER_TYPE")).thenReturn(newTriggerDef)
        `when`(achievementRepository.save(any(Achievement::class.java))).thenAnswer { it.arguments[0] }

        val result = adminAchievementManagementService.updateAchievement(existingAchievement.id!!, updateDto)

        assertNotNull(result)
        assertEquals("NEW_TRIGGER_TYPE", result.triggerTypeKey)
        assertEquals(mapOf("newParam" to "value"), result.triggerParameters)
        verify(achievementRepository).save(any(Achievement::class.java))
    }

    @Test
    fun `updateAchievement should throw IllegalArgumentException for invalid triggerTypeKey update`() {
        val updateDto = AdminUpdateAchievementDTO(triggerTypeKey = "INVALID_KEY")
        `when`(achievementRepository.findById(existingAchievement.id!!)).thenReturn(Optional.of(existingAchievement))
        `when`(achievementTriggerDefinitionService.getByKey("INVALID_KEY")).thenReturn(null)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            adminAchievementManagementService.updateAchievement(existingAchievement.id!!, updateDto)
        }
        assertTrue(exception.message!!.contains("Invalid triggerTypeKey: INVALID_KEY"))
        verify(achievementRepository, never()).save(any(Achievement::class.java))
    }

    @Test
    fun `updateAchievement should throw IllegalArgumentException for parameter mismatch on triggerTypeKey update`() {
        val updateDto = AdminUpdateAchievementDTO(
            triggerTypeKey = "NEW_TRIGGER_TYPE",
            triggerParameters = mapOf("wrongParam" to "value") // 'newParam' expected
        )
        val newTriggerDef = sampleTriggerDefinition.copy(key = "NEW_TRIGGER_TYPE", parametersSchema = mapOf("newParam" to "string"))

        `when`(achievementRepository.findById(existingAchievement.id!!)).thenReturn(Optional.of(existingAchievement))
        `when`(achievementTriggerDefinitionService.getByKey("NEW_TRIGGER_TYPE")).thenReturn(newTriggerDef)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            adminAchievementManagementService.updateAchievement(existingAchievement.id!!, updateDto)
        }
        assertTrue(exception.message!!.contains("Missing required parameter 'newParam'"))
        verify(achievementRepository, never()).save(any(Achievement::class.java))
    }
    
    @Test
    fun `updateAchievement should validate parameters against existing triggerTypeKey if only parameters are updated`() {
        // Existing achievement uses "COURSE_COMPLETION_COUNT" which expects "threshold" (integer)
        val updateDto = AdminUpdateAchievementDTO(triggerParameters = mapOf("threshold" to "not-an-integer"))

        `when`(achievementRepository.findById(existingAchievement.id!!)).thenReturn(Optional.of(existingAchievement))
        // Service will call getByKey for the *existing* achievement's triggerTypeKey
        `when`(achievementTriggerDefinitionService.getByKey(existingAchievement.triggerTypeKey)).thenReturn(sampleTriggerDefinition)


        val exception = assertThrows(IllegalArgumentException::class.java) {
            adminAchievementManagementService.updateAchievement(existingAchievement.id!!, updateDto)
        }
        assertTrue(exception.message!!.contains("Parameter 'threshold' for trigger 'COURSE_COMPLETION_COUNT' must be an integer"))
        verify(achievementRepository, never()).save(any(Achievement::class.java))
    }
    
    @Test
    fun `updateAchievement should succeed if only parameters are updated and are valid for existing triggerTypeKey`() {
        val updateDto = AdminUpdateAchievementDTO(triggerParameters = mapOf("threshold" to 10)) // Valid for COURSE_COMPLETION_COUNT

        `when`(achievementRepository.findById(existingAchievement.id!!)).thenReturn(Optional.of(existingAchievement))
        `when`(achievementTriggerDefinitionService.getByKey(existingAchievement.triggerTypeKey)).thenReturn(sampleTriggerDefinition)
        `when`(achievementRepository.save(any(Achievement::class.java))).thenAnswer { it.arguments[0] }

        val result = adminAchievementManagementService.updateAchievement(existingAchievement.id!!, updateDto)

        assertNotNull(result)
        assertEquals(existingAchievement.triggerTypeKey, result.triggerTypeKey) // Unchanged
        assertEquals(mapOf("threshold" to 10), result.triggerParameters) // Updated
        verify(achievementRepository).save(any(Achievement::class.java))
    }

    @Test
    fun `updateAchievement should throw NoSuchElementException if achievement not found`() {
        val updateDto = AdminUpdateAchievementDTO(name = "New Name")
        `when`(achievementRepository.findById("unknown-id")).thenReturn(Optional.empty())

        assertThrows(NoSuchElementException::class.java) {
            adminAchievementManagementService.updateAchievement("unknown-id", updateDto)
        }
        verify(achievementTriggerDefinitionService, never()).getByKey(anyString())
        verify(achievementRepository, never()).save(any(Achievement::class.java))
    }

    @Test
    fun `updateAchievement should throw IllegalArgumentException if new key is already taken`() {
        val updateDto = AdminUpdateAchievementDTO(key = "takenKey")
        val otherAchievementWithTakenKey = existingAchievement.copy(id="other-id", key = "takenKey")

        `when`(achievementRepository.findById(existingAchievement.id!!)).thenReturn(Optional.of(existingAchievement))
        `when`(achievementRepository.findByKey("takenKey")).thenReturn(otherAchievementWithTakenKey)


        val exception = assertThrows(IllegalArgumentException::class.java) {
            adminAchievementManagementService.updateAchievement(existingAchievement.id!!, updateDto)
        }
        assertTrue(exception.message!!.contains("Достижение с ключом 'takenKey' уже занято другим достижением."))
        verify(achievementRepository, never()).save(any(Achievement::class.java))
    }
}
