package com.makkenzo.codehorizon.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.makkenzo.codehorizon.dtos.AdminCreateAchievementDTO
import com.makkenzo.codehorizon.dtos.AdminUpdateAchievementDTO
import com.makkenzo.codehorizon.models.Achievement
import com.makkenzo.codehorizon.models.AchievementRarity
import com.makkenzo.codehorizon.models.AchievementTriggerDefinition
import com.makkenzo.codehorizon.repositories.AchievementRepository
import com.makkenzo.codehorizon.repositories.AchievementTriggerDefinitionRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.Instant

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(authorities = ["achievement:admin:manage"]) // Authority for achievement management
class AdminAchievementControllerIT {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var achievementRepository: AchievementRepository

    @Autowired
    private lateinit var achievementTriggerDefinitionRepository: AchievementTriggerDefinitionRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private lateinit var existingTriggerDef: AchievementTriggerDefinition
    private lateinit var existingAchievement: Achievement

    @BeforeEach
    fun setUp() {
        achievementRepository.deleteAll()
        achievementTriggerDefinitionRepository.deleteAll()

        val now = Instant.now()
        existingTriggerDef = AchievementTriggerDefinition(
            key = "COURSE_COMPLETED",
            name = "Course Completed",
            description = "Triggered when a course is completed.",
            parametersSchema = mapOf("courseId" to "string", "minLessons" to "integer"),
            createdAt = now,
            updatedAt = now
        )
        achievementTriggerDefinitionRepository.save(existingTriggerDef)

        existingAchievement = Achievement(
            key = "EXISTING_ACH",
            name = "Existing Achievement",
            description = "An achievement that already exists",
            iconUrl = "http://example.com/icon.png",
            triggerTypeKey = existingTriggerDef.key,
            triggerParameters = mapOf("courseId" to "course123", "minLessons" to 10),
            xpBonus = 100L,
            rarity = AchievementRarity.RARE,
            category = "General",
            order = 1
        )
        achievementRepository.save(existingAchievement)
    }

    @AfterEach
    fun tearDown() {
        achievementRepository.deleteAll()
        achievementTriggerDefinitionRepository.deleteAll()
    }

    @Test
    fun `createAchievement should succeed with valid triggerTypeKey and parameters`() {
        val createDto = AdminCreateAchievementDTO(
            key = "newAch",
            name = "New Achievement",
            description = "Desc for new",
            iconUrl = "http://example.com/new.png",
            triggerTypeKey = existingTriggerDef.key,
            triggerParameters = mapOf("courseId" to "courseXYZ", "minLessons" to 5),
            xpBonus = 50L,
            rarity = AchievementRarity.COMMON
        )

        mockMvc.perform(
            post("/api/admin/achievements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDto))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.key").value("newAch"))
            .andExpect(jsonPath("$.triggerTypeKey").value(existingTriggerDef.key))
            .andExpect(jsonPath("$.triggerParameters.courseId").value("courseXYZ"))

        assertTrue(achievementRepository.findByKey("newAch") != null)
    }

    @Test
    fun `createAchievement should fail with 400 for invalid triggerTypeKey`() {
        val createDto = AdminCreateAchievementDTO(
            key = "invalidTriggerAch",
            name = "Invalid Trigger Ach",
            description = "Desc",
            iconUrl = "http://example.com/invalid.png",
            triggerTypeKey = "NON_EXISTENT_TRIGGER_KEY", // This key does not exist
            triggerParameters = mapOf("param" to "value"),
            xpBonus = 10L,
            rarity = AchievementRarity.UNCOMMON
        )

        mockMvc.perform(
            post("/api/admin/achievements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDto))
        )
            .andExpect(status().isBadRequest) // Service throws IllegalArgumentException
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Invalid triggerTypeKey: NON_EXISTENT_TRIGGER_KEY")))


        assertFalse(achievementRepository.findByKey("invalidTriggerAch") != null)
    }

    @Test
    fun `createAchievement should fail with 400 for parameters not matching schema (missing required)`() {
        val createDto = AdminCreateAchievementDTO(
            key = "missingParamAch",
            name = "Missing Param Ach",
            description = "Desc",
            iconUrl = "http://example.com/missing.png",
            triggerTypeKey = existingTriggerDef.key, // Requires "courseId" and "minLessons"
            triggerParameters = mapOf("courseId" to "course123"), // "minLessons" is missing
            xpBonus = 20L,
            rarity = AchievementRarity.EPIC
        )

        mockMvc.perform(
            post("/api/admin/achievements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDto))
        )
            .andExpect(status().isBadRequest)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Missing required parameter 'minLessons'")))
    }

    @Test
    fun `createAchievement should fail with 400 for parameters not matching schema (type mismatch)`() {
        val createDto = AdminCreateAchievementDTO(
            key = "typeMismatchAch",
            name = "Type Mismatch Ach",
            description = "Desc",
            iconUrl = "http://example.com/mismatch.png",
            triggerTypeKey = existingTriggerDef.key, // "minLessons" should be integer
            triggerParameters = mapOf("courseId" to "course789", "minLessons" to "not-an-integer"),
            xpBonus = 30L,
            rarity = AchievementRarity.LEGENDARY
        )

        mockMvc.perform(
            post("/api/admin/achievements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDto))
        )
            .andExpect(status().isBadRequest)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Parameter 'minLessons' for trigger '${existingTriggerDef.key}' must be an integer")))
    }
    
    @Test
    fun `createAchievement should fail with 400 for extraneous parameter`() {
        val createDto = AdminCreateAchievementDTO(
            key = "extraneousParamAch",
            name = "Extraneous Param Ach",
            description = "Desc",
            iconUrl = "http://example.com/extraneous.png",
            triggerTypeKey = existingTriggerDef.key,
            triggerParameters = mapOf("courseId" to "courseABC", "minLessons" to 10, "extraStuff" to "notAllowed"),
            xpBonus = 25L,
            rarity = AchievementRarity.RARE
        )

        mockMvc.perform(
            post("/api/admin/achievements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDto))
        )
            .andExpect(status().isBadRequest)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Extraneous parameter 'extraStuff'")))
    }


    @Test
    fun `updateAchievement should succeed with valid triggerTypeKey and parameters change`() {
        val newTriggerDef = AchievementTriggerDefinition(
            key = "LESSON_COMPLETED",
            name = "Lesson Completed",
            description = "Triggered when a lesson is completed.",
            parametersSchema = mapOf("lessonId" to "string"),
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        achievementTriggerDefinitionRepository.save(newTriggerDef)

        val updateDto = AdminUpdateAchievementDTO(
            triggerTypeKey = newTriggerDef.key,
            triggerParameters = mapOf("lessonId" to "lessonXYZ")
        )

        mockMvc.perform(
            put("/api/admin/achievements/${existingAchievement.id}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.triggerTypeKey").value(newTriggerDef.key))
            .andExpect(jsonPath("$.triggerParameters.lessonId").value("lessonXYZ"))

        val updatedAch = achievementRepository.findById(existingAchievement.id!!).get()
        assertEquals(newTriggerDef.key, updatedAch.triggerTypeKey)
        assertEquals("lessonXYZ", updatedAch.triggerParameters["lessonId"])
    }
    
    @Test
    fun `updateAchievement should succeed when only parameters are updated and valid`() {
        val updateDto = AdminUpdateAchievementDTO(
            triggerParameters = mapOf("courseId" to "updatedCourse456", "minLessons" to 15)
        )

        mockMvc.perform(
            put("/api/admin/achievements/${existingAchievement.id}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.triggerTypeKey").value(existingTriggerDef.key)) // Should remain the same
            .andExpect(jsonPath("$.triggerParameters.courseId").value("updatedCourse456"))
            .andExpect(jsonPath("$.triggerParameters.minLessons").value(15))

        val updatedAch = achievementRepository.findById(existingAchievement.id!!).get()
        assertEquals(existingTriggerDef.key, updatedAch.triggerTypeKey)
        assertEquals("updatedCourse456", updatedAch.triggerParameters["courseId"])
        assertEquals(15, updatedAch.triggerParameters["minLessons"])
    }


    @Test
    fun `updateAchievement should fail with 400 for invalid triggerTypeKey update`() {
        val updateDto = AdminUpdateAchievementDTO(triggerTypeKey = "NON_EXISTENT_KEY")

        mockMvc.perform(
            put("/api/admin/achievements/${existingAchievement.id}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto))
        )
            .andExpect(status().isBadRequest)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Invalid triggerTypeKey: NON_EXISTENT_KEY")))
    }

    @Test
    fun `updateAchievement should fail with 400 for parameters not matching schema on update`() {
        val updateDto = AdminUpdateAchievementDTO(
            triggerParameters = mapOf("courseId" to "course123", "minLessons" to "not-an-integer") // type mismatch
        )

        mockMvc.perform(
            put("/api/admin/achievements/${existingAchievement.id}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto))
        )
            .andExpect(status().isBadRequest)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Parameter 'minLessons' for trigger '${existingTriggerDef.key}' must be an integer")))
    }
    
    @Test
    fun `updateAchievement should return 404 if achievement not found`() {
        val updateDto = AdminUpdateAchievementDTO(name = "New Name")
        mockMvc.perform(
            put("/api/admin/achievements/nonExistentId")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto))
        )
            .andExpect(status().isNotFound)
    }


    // Test other CRUD operations to ensure they still work
    @Test
    fun `getAchievementById should still work`() {
        mockMvc.perform(get("/api/admin/achievements/${existingAchievement.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.key").value(existingAchievement.key))
    }

    @Test
    fun `getAllAchievements should still work`() {
        mockMvc.perform(get("/api/admin/achievements?page=0&size=10"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].key").value(existingAchievement.key))
    }

    @Test
    fun `deleteAchievement should still work`() {
        mockMvc.perform(delete("/api/admin/achievements/${existingAchievement.id}"))
            .andExpect(status().isNoContent)
        assertFalse(achievementRepository.existsById(existingAchievement.id!!))
    }
}
