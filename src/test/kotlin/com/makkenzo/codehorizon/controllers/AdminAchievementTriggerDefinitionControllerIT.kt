package com.makkenzo.codehorizon.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.makkenzo.codehorizon.dtos.CreateAchievementTriggerDefinitionDTO
import com.makkenzo.codehorizon.dtos.UpdateAchievementTriggerDefinitionDTO
import com.makkenzo.codehorizon.models.AchievementTriggerDefinition
import com.makkenzo.codehorizon.repositories.AchievementTriggerDefinitionRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.Instant

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test") // Ensure your test profile uses embedded mongo or a test DB
@WithMockUser(authorities = ["admin:achievement_trigger_definitions"]) // Mock user with required authority
class AdminAchievementTriggerDefinitionControllerIT {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var repository: AchievementTriggerDefinitionRepository

    @Autowired
    private lateinit var mongoTemplate: MongoTemplate // For direct DB interaction if needed

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private lateinit var sampleDefinition1: AchievementTriggerDefinition
    private lateinit var sampleDefinition2: AchievementTriggerDefinition

    @BeforeEach
    fun setUp() {
        repository.deleteAll() // Clean up before each test

        val now = Instant.now()
        sampleDefinition1 = AchievementTriggerDefinition(
            key = "testKey1",
            name = "Test Definition 1",
            description = "Description 1",
            parametersSchema = mapOf("param1" to "string"),
            createdAt = now,
            updatedAt = now
        )
        sampleDefinition2 = AchievementTriggerDefinition(
            key = "testKey2",
            name = "Test Definition 2",
            description = "Description 2",
            parametersSchema = mapOf("count" to "integer"),
            createdAt = now,
            updatedAt = now
        )
        repository.saveAll(listOf(sampleDefinition1, sampleDefinition2))
    }

    @AfterEach
    fun tearDown() {
        repository.deleteAll()
    }

    @Test
    fun `createDefinition should create and return new definition with 201 status`() {
        val createDto = CreateAchievementTriggerDefinitionDTO(
            key = "newKey",
            name = "New Definition",
            description = "New Desc",
            parametersSchema = mapOf("flag" to "boolean")
        )

        mockMvc.perform(
            post("/api/admin/achievement-trigger-definitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDto))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.key").value("newKey"))
            .andExpect(jsonPath("$.name").value("New Definition"))
            .andExpect(jsonPath("$.parametersSchema.flag").value("boolean"))

        assertTrue(repository.existsByKey("newKey"))
    }

    @Test
    fun `createDefinition should return 400 for duplicate key`() {
        val createDto = CreateAchievementTriggerDefinitionDTO(
            key = "testKey1", // Existing key
            name = "Duplicate Key Def",
            description = "Desc",
            parametersSchema = emptyMap()
        )

        mockMvc.perform(
            post("/api/admin/achievement-trigger-definitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDto))
        )
            .andExpect(status().isBadRequest) // Based on service's IllegalArgumentException
    }
    
    @Test
    fun `createDefinition should return 400 for invalid DTO (e_g_ blank key)`() {
        val createDto = CreateAchievementTriggerDefinitionDTO(
            key = "", // Invalid: blank
            name = "Invalid Def",
            description = "Desc",
            parametersSchema = emptyMap()
        )

        mockMvc.perform(
            post("/api/admin/achievement-trigger-definitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDto))
        )
            .andExpect(status().isBadRequest)
    }


    @Test
    fun `getDefinitionByKey should return definition with 200 status when found`() {
        mockMvc.perform(get("/api/admin/achievement-trigger-definitions/${sampleDefinition1.key}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.key").value(sampleDefinition1.key))
            .andExpect(jsonPath("$.name").value(sampleDefinition1.name))
    }

    @Test
    fun `getDefinitionByKey should return 404 status when not found`() {
        mockMvc.perform(get("/api/admin/achievement-trigger-definitions/nonExistentKey"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `getAllDefinitions should return page of definitions`() {
        mockMvc.perform(get("/api/admin/achievement-trigger-definitions?page=0&size=10"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[0].key").value(sampleDefinition1.key)) // Default sort might vary
    }

    @Test
    fun `updateDefinition should update and return definition with 200 status`() {
        val updateDto = UpdateAchievementTriggerDefinitionDTO(
            name = "Updated Name",
            description = "Updated Description",
            parametersSchema = mapOf("param1" to "string", "added" to "boolean")
        )

        mockMvc.perform(
            put("/api/admin/achievement-trigger-definitions/${sampleDefinition1.key}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Updated Name"))
            .andExpect(jsonPath("$.description").value("Updated Description"))
            .andExpect(jsonPath("$.parametersSchema.added").value("boolean"))

        val updatedDef = repository.findByKey(sampleDefinition1.key)
        assertNotNull(updatedDef)
        assertEquals("Updated Name", updatedDef?.name)
    }
    
    @Test
    fun `updateDefinition should return 400 for invalid DTO (e_g_ name too short)`() {
        // Assuming @Size(min=3) on name in Update DTO, not explicitly in prompt but good practice
        val updateDto = UpdateAchievementTriggerDefinitionDTO(name = "U", description = null, parametersSchema = null)


        mockMvc.perform(
            put("/api/admin/achievement-trigger-definitions/${sampleDefinition1.key}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `updateDefinition should return 404 when definition to update is not found`() {
        val updateDto = UpdateAchievementTriggerDefinitionDTO(name = "Doesn't Matter", description = null, parametersSchema = null)
        mockMvc.perform(
            put("/api/admin/achievement-trigger-definitions/nonExistentKey")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto))
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `deleteDefinition should return 204 status when successful`() {
        mockMvc.perform(delete("/api/admin/achievement-trigger-definitions/${sampleDefinition1.key}"))
            .andExpect(status().isNoContent)

        assertFalse(repository.existsByKey(sampleDefinition1.key))
    }

    @Test
    fun `deleteDefinition should return 404 status when definition to delete is not found`() {
        mockMvc.perform(delete("/api/admin/achievement-trigger-definitions/nonExistentKey"))
            .andExpect(status().isNotFound)
    }
}
