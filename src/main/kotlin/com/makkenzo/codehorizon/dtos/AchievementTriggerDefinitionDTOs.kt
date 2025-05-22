package com.makkenzo.codehorizon.dtos

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

data class CreateAchievementTriggerDefinitionDTO(
    @field:NotBlank(message = "Key cannot be blank")
    @field:Size(min = 3, max = 100, message = "Key must be between 3 and 100 characters")
    val key: String,

    @field:NotBlank(message = "Name cannot be blank")
    @field:Size(min = 3, max = 150, message = "Name must be between 3 and 150 characters")
    val name: String,

    @field:NotBlank(message = "Description cannot be blank")
    @field:Size(min = 10, max = 500, message = "Description must be between 10 and 500 characters")
    val description: String,

    @field:NotEmpty(message = "Parameters schema cannot be empty")
    // TODO: Add validation for map content, e.g., ensuring values are simple type names like "string", "integer", "boolean"
    val parametersSchema: Map<String, String>
)

data class UpdateAchievementTriggerDefinitionDTO(
    @field:Size(min = 3, max = 150, message = "Name must be between 3 and 150 characters")
    val name: String?,

    @field:Size(min = 10, max = 500, message = "Description must be between 10 and 500 characters")
    val description: String?,

    // TODO: Add validation for map content if feasible
    val parametersSchema: Map<String, String>?
)
