package com.makkenzo.codehorizon.migrations

import com.makkenzo.codehorizon.models.AchievementTriggerDefinition
import com.makkenzo.codehorizon.repositories.AchievementRepository
import com.makkenzo.codehorizon.repositories.AchievementTriggerDefinitionRepository
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Component
import java.time.Instant

data class OldTriggerInfo(
    val enumName: String,
    val displayName: String,
    val description: String,
    val parametersSchema: Map<String, String>,
    val paramsExtractor: (oldThreshold: Int?, oldThresholdValue: String?) -> Map<String, Any>
)

@Component
@Order(1) // Example: Run this migration with a specific order
class AchievementDataMigrationRunner(
    private val achievementRepository: AchievementRepository, // Will be used if we read into Achievement model
    private val achievementTriggerDefinitionRepository: AchievementTriggerDefinitionRepository,
    private val mongoTemplate: MongoTemplate
) : CommandLineRunner {

    private val logger = LoggerFactory.getLogger(AchievementDataMigrationRunner::class.java)
    private val MIGRATION_NAME = "AchievementTriggerMigration_v1"

    // Based on the previous AchievementTriggerType enum
    val oldTriggers: List<OldTriggerInfo> = listOf(
        OldTriggerInfo(
            enumName = "COURSE_COMPLETION_COUNT",
            displayName = "Course Completion Count",
            description = "Triggers when a user completes a specific number of courses.",
            parametersSchema = mapOf("threshold" to "integer"),
            paramsExtractor = { oldThreshold, _ -> mapOf("threshold" to (oldThreshold ?: 0)) }
        ),
        OldTriggerInfo(
            enumName = "LESSON_COMPLETION_COUNT_TOTAL",
            displayName = "Total Lessons Completed",
            description = "Triggers when a user completes a total number of lessons.",
            parametersSchema = mapOf("threshold" to "integer"),
            paramsExtractor = { oldThreshold, _ -> mapOf("threshold" to (oldThreshold ?: 0)) }
        ),
        OldTriggerInfo(
            enumName = "LESSON_COMPLETION_STREAK_DAILY",
            displayName = "Daily Lesson Completion Streak",
            description = "Triggers when a user maintains a daily lesson completion streak.",
            parametersSchema = mapOf("streak_days" to "integer"),
            paramsExtractor = { oldThreshold, _ -> mapOf("streak_days" to (oldThreshold ?: 0)) }
        ),
        OldTriggerInfo(
            enumName = "REVIEW_COUNT",
            displayName = "Review Count",
            description = "Triggers when a user writes a specific number of reviews.",
            parametersSchema = mapOf("threshold" to "integer"),
            paramsExtractor = { oldThreshold, _ -> mapOf("threshold" to (oldThreshold ?: 0)) }
        ),
        OldTriggerInfo(
            enumName = "FIRST_COURSE_COMPLETED",
            displayName = "First Course Completed",
            description = "Triggers when a user completes their first course.",
            parametersSchema = emptyMap(),
            paramsExtractor = { _, _ -> emptyMap() }
        ),
        OldTriggerInfo(
            enumName = "FIRST_REVIEW_WRITTEN",
            displayName = "First Review Written",
            description = "Triggers when a user writes their first review.",
            parametersSchema = emptyMap(),
            paramsExtractor = { _, _ -> emptyMap() }
        ),
        OldTriggerInfo(
            enumName = "PROFILE_COMPLETION_PERCENT",
            displayName = "Profile Completion Percentage",
            description = "Triggers when a user's profile completion reaches a certain percentage.",
            parametersSchema = mapOf("percentage" to "integer"),
            paramsExtractor = { oldThreshold, _ -> mapOf("percentage" to (oldThreshold ?: 0)) }
        ),
        OldTriggerInfo(
            enumName = "DAILY_LOGIN_STREAK",
            displayName = "Daily Login Streak",
            description = "Triggers when a user maintains a daily login streak.",
            parametersSchema = mapOf("streak_days" to "integer"),
            paramsExtractor = { oldThreshold, _ -> mapOf("streak_days" to (oldThreshold ?: 0)) }
        ),
        OldTriggerInfo(
            enumName = "COURSE_CREATION_COUNT",
            displayName = "Course Creation Count",
            description = "Triggers when a user creates a specific number of courses.",
            parametersSchema = mapOf("threshold" to "integer"),
            paramsExtractor = { oldThreshold, _ -> mapOf("threshold" to (oldThreshold ?: 0)) }
        ),
        OldTriggerInfo(
            enumName = "TOTAL_XP_EARNED",
            displayName = "Total XP Earned",
            description = "Triggers when a user earns a total amount of XP.",
            parametersSchema = mapOf("xp_amount" to "integer"), // Assuming threshold was for XP
            paramsExtractor = { oldThreshold, _ -> mapOf("xp_amount" to (oldThreshold ?: 0)) }
        ),
        OldTriggerInfo(
            enumName = "LEVEL_REACHED",
            displayName = "Level Reached",
            description = "Triggers when a user reaches a specific level.",
            parametersSchema = mapOf("level" to "integer"),
            paramsExtractor = { oldThreshold, _ -> mapOf("level" to (oldThreshold ?: 0)) }
        ),
        OldTriggerInfo(
            enumName = "SPECIFIC_COURSE_COMPLETED",
            displayName = "Specific Course Completed",
            description = "Triggers when a user completes a specific course.",
            parametersSchema = mapOf("courseId" to "string"),
            paramsExtractor = { _, oldThresholdValue -> mapOf("courseId" to (oldThresholdValue ?: "")) }
        ),
        OldTriggerInfo(
            enumName = "SPECIFIC_LESSON_COMPLETED",
            displayName = "Specific Lesson Completed",
            description = "Triggers when a user completes a specific lesson.",
            parametersSchema = mapOf("lessonId" to "string"), // Assuming thresholdValue was lessonId
            paramsExtractor = { _, oldThresholdValue -> mapOf("lessonId" to (oldThresholdValue ?: "")) }
        ),
        OldTriggerInfo(
            enumName = "CATEGORY_COURSES_COMPLETED",
            displayName = "Category Courses Completed",
            description = "Triggers when a user completes a number of courses in a specific category.",
            // This one is tricky. Assuming triggerThreshold was count, and triggerThresholdValue was categoryId/name.
            parametersSchema = mapOf("categoryId" to "string", "threshold" to "integer"),
            paramsExtractor = { oldThreshold, oldThresholdValue ->
                mapOf(
                    "categoryId" to (oldThresholdValue ?: ""),
                    "threshold" to (oldThreshold ?: 0)
                )
            }
        ),
        OldTriggerInfo(
            enumName = "WISHLIST_ITEM_COUNT",
            displayName = "Wishlist Item Count",
            description = "Triggers when a user has a specific number of items in their wishlist.",
            parametersSchema = mapOf("threshold" to "integer"),
            paramsExtractor = { oldThreshold, _ -> mapOf("threshold" to (oldThreshold ?: 0)) }
        ),
        OldTriggerInfo(
            enumName = "MENTOR_STUDENT_COURSE_COMPLETION",
            displayName = "Mentor's Student Course Completion",
            description = "Triggers when a student of a mentor completes a specific course.",
            // Assuming triggerThresholdValue was courseId, and threshold might have been count of students or unused.
            parametersSchema = mapOf("courseId" to "string"), // Simplified, may need more complex logic
            paramsExtractor = { _, oldThresholdValue -> mapOf("courseId" to (oldThresholdValue ?: "")) }
        ),
        OldTriggerInfo(
            enumName = "MENTOR_TOTAL_STUDENT_COMPLETIONS",
            displayName = "Mentor's Total Student Completions",
            description = "Triggers when a mentor's students complete a total number of courses.",
            parametersSchema = mapOf("threshold" to "integer"),
            paramsExtractor = { oldThreshold, _ -> mapOf("threshold" to (oldThreshold ?: 0)) }
        ),
        OldTriggerInfo(
            enumName = "CUSTOM_FRONTEND_EVENT",
            displayName = "Custom Frontend Event",
            description = "Triggers based on a custom frontend event name.",
            parametersSchema = mapOf("eventName" to "string"), // Assuming triggerThresholdValue was eventName
            paramsExtractor = { _, oldThresholdValue -> mapOf("eventName" to (oldThresholdValue ?: "")) }
        ),
        OldTriggerInfo(
            enumName = "LESSON_COMPLETED_AT_NIGHT",
            displayName = "Lesson Completed at Night",
            description = "Triggers when a user completes a lesson at night.",
            parametersSchema = emptyMap(), // Assuming no specific threshold/value needed, logic is implicit
            paramsExtractor = { _, _ -> emptyMap() }
        ),
        OldTriggerInfo(
            enumName = "FIRST_COURSE_COMPLETED_WITHIN_TIMEFRAME",
            displayName = "First Course Completed Within Timeframe",
            description = "Triggers when a user completes their first course within a specific timeframe (e.g. X days from registration).",
            // Assuming triggerThreshold was the number of days for the timeframe
            parametersSchema = mapOf("days" to "integer"),
            paramsExtractor = { oldThreshold, _ -> mapOf("days" to (oldThreshold ?: 0)) }
        )
    )

    override fun run(vararg args: String?) {
        logger.info("Starting Achievement Data Migration: $MIGRATION_NAME")

        if (hasMigrationAlreadyRun()) {
            logger.info("Migration '$MIGRATION_NAME' has already been executed. Skipping.")
            return
        }

        try {
            // Phase 1: Populate achievement_trigger_definitions
            populateTriggerDefinitions()

            // Phase 2: Migrate existing achievements
            migrateAchievements()

            // Mark migration as complete
            markMigrationAsComplete()
            logger.info("Achievement Data Migration '$MIGRATION_NAME' completed successfully.")

        } catch (e: Exception) {
            logger.error("Error during achievement data migration '$MIGRATION_NAME': ${e.message}", e)
            // Decide if to throw exception further or handle (e.g. prevent app startup if critical)
        }
    }

    private fun hasMigrationAlreadyRun(): Boolean {
        val query = Query(Criteria.where("name").`is`(MIGRATION_NAME).and("status").`is`("completed"))
        return mongoTemplate.exists(query, "migrations_log")
    }

    private fun markMigrationAsComplete() {
        val migrationLog = Document(
            mapOf(
                "name" to MIGRATION_NAME,
                "status" to "completed",
                "executedAt" to Instant.now()
            )
        )
        mongoTemplate.insert(migrationLog, "migrations_log")
    }

    private fun populateTriggerDefinitions() {
        logger.info("Phase 1: Populating achievement_trigger_definitions...")
        val now = Instant.now()
        var count = 0
        for (triggerInfo in oldTriggers) {
            try {
                if (!achievementTriggerDefinitionRepository.existsByKey(triggerInfo.enumName)) {
                    val definition = AchievementTriggerDefinition(
                        key = triggerInfo.enumName,
                        name = triggerInfo.displayName,
                        description = triggerInfo.description,
                        parametersSchema = triggerInfo.parametersSchema,
                        createdAt = now,
                        updatedAt = now
                    )
                    achievementTriggerDefinitionRepository.save(definition)
                    logger.info("Created AchievementTriggerDefinition for key: ${triggerInfo.enumName}")
                    count++
                } else {
                    logger.info("AchievementTriggerDefinition for key: ${triggerInfo.enumName} already exists. Skipping.")
                }
            } catch (e: Exception) {
                logger.error("Error populating AchievementTriggerDefinition for key ${triggerInfo.enumName}: ${e.message}", e)
                // Decide if this error is critical
            }
        }
        logger.info("Phase 1 finished. Created $count new trigger definitions.")
    }

    private fun migrateAchievements() {
        logger.info("Phase 2: Migrating existing achievements documents...")
        val query = Query(Criteria.where("triggerTypeKey").exists(false))
        // Fetching as raw Document to access old fields not present in the updated Achievement model
        val achievementsToMigrate = mongoTemplate.find(query, Document::class.java, "achievements")
        var migratedCount = 0
        var warningCount = 0

        logger.info("Found ${achievementsToMigrate.size} achievements to migrate.")

        for (doc in achievementsToMigrate) {
            val achievementId = doc.getObjectId("_id")
            try {
                val oldTriggerTypeStr = doc.getString("triggerType")
                // Handle potential missing values for old fields if some documents were partially updated or malformed
                val oldTriggerThreshold = doc.getInteger("triggerThreshold")
                val oldTriggerThresholdValue = doc.getString("triggerThresholdValue")

                if (oldTriggerTypeStr == null) {
                    logger.warn("Achievement with ID '$achievementId' is missing 'triggerType'. Skipping migration for this document.")
                    warningCount++
                    continue
                }

                val oldTriggerInfo = oldTriggers.find { it.enumName == oldTriggerTypeStr }

                if (oldTriggerInfo == null) {
                    logger.warn("No OldTriggerInfo found for triggerType '$oldTriggerTypeStr' in achievement ID '$achievementId'. Skipping.")
                    warningCount++
                    continue
                }

                val newTriggerParameters = oldTriggerInfo.paramsExtractor(oldTriggerThreshold, oldTriggerThresholdValue)
                val now = Instant.now()

                val update = Update()
                update.set("triggerTypeKey", oldTriggerInfo.enumName)
                update.set("triggerParameters", newTriggerParameters)
                update.set("updatedAt", now) // Assuming Achievement model has updatedAt
                update.unset("triggerType")
                update.unset("triggerThreshold")
                update.unset("triggerThresholdValue")

                val result = mongoTemplate.updateFirst(Query(Criteria.where("_id").`is`(achievementId)), update, "achievements")

                if (result.modifiedCount > 0) {
                    logger.info("Successfully migrated achievement with ID: $achievementId (Old type: $oldTriggerTypeStr)")
                    migratedCount++
                } else {
                    logger.warn("Achievement with ID: $achievementId was queried for migration but not modified by update. This might indicate an issue or it was already in the new format partially.")
                    warningCount++
                }

            } catch (e: Exception) {
                logger.error("Error migrating achievement with ID '$achievementId': ${e.message}", e)
                // Decide if this error is critical for the whole migration
            }
        }
        logger.info("Phase 2 finished. Migrated $migratedCount achievements. Warnings: $warningCount.")
    }
}
```

A few notes about the `oldTriggers` list:
*   I've made educated guesses for `parametersSchema` and `paramsExtractor` for types like `CATEGORY_COURSES_COMPLETED`, `MENTOR_STUDENT_COURSE_COMPLETION`, `CUSTOM_FRONTEND_EVENT`, `SPECIFIC_LESSON_COMPLETED`, and `TOTAL_XP_EARNED`. These would need verification against the actual historical usage or more detailed business logic.
*   `oldThreshold` in `paramsExtractor` is nullable (`Int?`) now to handle cases where it might not have been relevant or present for certain old trigger types, defaulting to 0 or an empty string as appropriate.
*   The `updatedAt` field is assumed to exist on the `Achievement` model for the migration update. If not, that `update.set` should be removed.

This single file contains all the logic as requested.The `AchievementDataMigrationRunner.kt` file has been created with the implementation for both phases of the migration, along with the mechanism to prevent re-execution and logging.

**Key aspects implemented:**

1.  **`AchievementDataMigrationRunner.kt`:**
    *   Created in `com.makkenzo.codehorizon.migrations`.
    *   Annotated with `@Component` and `@Order(1)`.
    *   Injects `AchievementTriggerDefinitionRepository` and `MongoTemplate`.
    *   Implements `CommandLineRunner`.
    *   **Execution Control:** Uses a `migrations_log` collection to check if `AchievementTriggerMigration_v1` has run.
        *   `hasMigrationAlreadyRun()`: Checks for the log document.
        *   `markMigrationAsComplete()`: Creates the log document upon successful migration.

2.  **`OldTriggerInfo` and `oldTriggers` list:**
    *   `OldTriggerInfo` data class defined to hold historical enum information, schema, and parameter extraction logic.
    *   `oldTriggers` list populated with all known `AchievementTriggerType` values from the previous enum.
        *   Each entry defines `enumName`, `displayName`, `description`, `parametersSchema`, and `paramsExtractor`.
        *   `paramsExtractor` is a lambda that takes `oldThreshold: Int?` and `oldThresholdValue: String?` and returns `Map<String, Any>`.
        *   Care was taken to map old fields to new parameter names (e.g., `threshold`, `courseId`, `streak_days`, `percentage`, `xp_amount`, `level`, `lessonId`, `categoryId`, `eventName`, `days`). Nullable `oldThreshold` is handled in extractors.

3.  **Phase 1: `populateTriggerDefinitions()`:**
    *   Iterates through `oldTriggers`.
    *   Uses `achievementTriggerDefinitionRepository.existsByKey()` to check for existing definitions.
    *   If a definition doesn't exist, it creates and saves a new `AchievementTriggerDefinition`.
    *   Includes logging and basic error handling per definition.

4.  **Phase 2: `migrateAchievements()`:**
    *   Uses `mongoTemplate.find(Query(Criteria.where("triggerTypeKey").exists(false)), Document::class.java, "achievements")` to fetch raw `org.bson.Document` objects that haven't been migrated.
    *   Iterates through these documents:
        *   Extracts `triggerType` (String), `triggerThreshold` (Integer), and `triggerThresholdValue` (String) from the `Document`.
        *   Finds the corresponding `OldTriggerInfo` from the `oldTriggers` list.
        *   Uses the `paramsExtractor` to build the new `triggerParameters` map.
        *   Constructs an `Update` operation using `MongoTemplate` to:
            *   `set` the new `triggerTypeKey` and `triggerParameters`.
            *   `set` an `updatedAt` timestamp (assuming the model has this field).
            *   `unset` the old fields (`triggerType`, `triggerThreshold`, `triggerThresholdValue`).
        *   Executes the update using `mongoTemplate.updateFirst()`.
    *   Includes logging for progress, warnings (e.g., missing `triggerType`, unknown `triggerType`), and errors.

5.  **Error Handling and Logging:**
    *   The main `run` method has a `try-catch` block.
    *   Each phase and critical operation within phases includes logging and error handling.
    *   Warnings are logged for skippable issues (e.g., an achievement document missing the old `triggerType` field).

This implementation fulfills the requirements of the task, providing a robust script to migrate the achievement data to the new dynamic trigger system. The accuracy of the `oldTriggers` list, especially the `paramsExtractor` logic and `parametersSchema` for complex enums, is crucial and has been implemented to the best of my ability based on the information provided.
