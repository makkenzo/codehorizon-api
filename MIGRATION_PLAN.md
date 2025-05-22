# MongoDB Migration Plan for Dynamic Achievement Triggers

This document outlines the steps to migrate existing MongoDB data to support the new dynamic achievement trigger system.

## Phase 1: Populate `achievement_trigger_definitions` Collection

The first step is to populate the new `achievement_trigger_definitions` collection. This collection will hold the definitions for each type of achievement trigger. We will create one document in this collection for each value in the old `AchievementTriggerType` enum.

**Process:**

1.  **Iterate through `AchievementTriggerType` enum values:** Programmatically access each value of the (now removed, but historically known) `AchievementTriggerType` enum.
2.  **For each enum value, create a new `AchievementTriggerDefinition` document:**
    *   `key`: Set to the string representation of the enum value (e.g., `"COURSE_COMPLETION_COUNT"`).
    *   `name`: A human-readable version of the key (e.g., "Course Completion Count"). This might require manual mapping or a utility function to generate from the enum name.
    *   `description`: A brief description of what the trigger does. This might also require manual input or be generated.
    *   `parametersSchema`: Define the expected parameters for this trigger type. This is the most crucial part and requires careful mapping from the old structure.
        *   Example for `COURSE_COMPLETION_COUNT`:
            `{"threshold": "integer"}`
        *   Example for `SPECIFIC_COURSE_COMPLETED`:
            `{"courseId": "string"}`
        *   Example for `LESSON_COMPLETION_STREAK_DAILY`:
            `{"streak_days": "integer"}`
    *   `createdAt`, `updatedAt`: Set to the current timestamp.
3.  **Insertion Tool:** This can be done using a script (e.g., a Kotlin script using the MongoDB Java driver, or a Spring Boot `ApplicationRunner` / `CommandLineRunner` that runs once on application startup).

**Example `AchievementTriggerDefinition` documents:**

```json
// For COURSE_COMPLETION_COUNT
{
  "key": "COURSE_COMPLETION_COUNT",
  "name": "Course Completion Count",
  "description": "Triggers when a user completes a specific number of courses.",
  "parametersSchema": { "threshold": "integer" },
  "createdAt": ISODate("..."),
  "updatedAt": ISODate("...")
}

// For SPECIFIC_COURSE_COMPLETED
{
  "key": "SPECIFIC_COURSE_COMPLETED",
  "name": "Specific Course Completed",
  "description": "Triggers when a user completes a specific course.",
  "parametersSchema": { "courseId": "string" },
  "createdAt": ISODate("..."),
  "updatedAt": ISODate("...")
}
```

## Phase 2: Migrate Existing `achievements` Documents

After the `achievement_trigger_definitions` collection is populated, existing documents in the `achievements` collection must be updated to the new schema.

**Process:**

1.  **Iterate through all documents in the `achievements` collection.** This can be done using a MongoDB query in a script or a Spring Boot batch job.
2.  **For each achievement document:**
    *   Read the old `triggerType` (enum value), `triggerThreshold` (integer), and `triggerThresholdValue` (string, nullable).
    *   **Determine the new `triggerTypeKey`:** This will be the string representation of the old `triggerType` enum value.
        *   Example: If `triggerType` was `AchievementTriggerType.COURSE_COMPLETION_COUNT`, then `triggerTypeKey` becomes `"COURSE_COMPLETION_COUNT"`.
    *   **Construct the new `triggerParameters` map:** This depends on the `triggerType`.
        *   If `triggerType` was `COURSE_COMPLETION_COUNT`:
            `triggerParameters = {"threshold": achievement.triggerThreshold}`
        *   If `triggerType` was `SPECIFIC_COURSE_COMPLETED`:
            `triggerParameters = {"courseId": achievement.triggerThresholdValue}` (Note: We need to decide on a consistent parameter name like `courseId` or `targetId` and ensure it matches the `parametersSchema` in the corresponding `AchievementTriggerDefinition`).
        *   If `triggerType` was `LESSON_COMPLETION_COUNT_TOTAL`:
            `triggerParameters = {"count": achievement.triggerThreshold}`
        *   If `triggerType` involved no additional value other than the type itself (e.g., `FIRST_COURSE_COMPLETED`), `triggerParameters` might be an empty map `{}` or contain a default/implicit value if the schema demands it.
            Example: `FIRST_COURSE_COMPLETED` -> `triggerTypeKey = "FIRST_COURSE_COMPLETED"`, `triggerParameters = {}`
        *   Careful mapping is required for each old enum value to ensure the `triggerParameters` keys and value types align with what was defined in the `parametersSchema` for that `triggerTypeKey` in Phase 1.
    *   **Update the achievement document:**
        *   Set the new `triggerTypeKey` field.
        *   Set the new `triggerParameters` field.
        *   Remove the old fields: `triggerType`, `triggerThreshold`, `triggerThresholdValue`.
        *   Update the `updatedAt` timestamp.
3.  **Tools for Migration:**
    *   **Spring Boot:** A `CommandLineRunner` or `ApplicationRunner` can be implemented to run the migration logic on application startup. This is suitable for one-time migrations. Spring Data MongoDB can be used for querying and updating documents.
    *   **Mongo Shell Scripts:** For simpler migrations or direct database manipulation, scripts written for `mongosh` can be used.
    *   **Dedicated Migration Tools:** Tools like Mongock or Flyway (though Flyway is more for SQL, concepts can be adapted) can manage schema versions and migrations more robustly, especially in production environments.

## Considerations & Rollback Strategy

*   **Data Backup:** Before running any migration scripts, ensure a full backup of the MongoDB database is taken.
*   **Testing:** Test the migration scripts thoroughly in a staging/development environment before running them in production.
*   **Idempotency:** Design migration scripts to be idempotent if possible, meaning they can be run multiple times without causing adverse effects or data corruption. This is useful if a script fails midway.
*   **Parameter Naming Consistency:** Decide on a consistent naming convention for parameter keys in `triggerParameters` and `parametersSchema` (e.g., `camelCase` or `snake_case`). The examples above used a mix; this should be standardized. For instance, if `triggerThresholdValue` previously held a course ID, the new parameter key in `triggerParameters` should be consistently named, e.g., `courseId`.
*   **Rollback Plan:**
    *   If the migration fails or causes issues, the primary rollback strategy would be to restore the database from the backup taken before the migration.
    *   Alternatively, a reverse migration script could be prepared, which would transform the new schema back to the old one. This is more complex and error-prone.

This migration process ensures that all existing achievement data is correctly transformed to the new dynamic trigger system, and the system is ready to define new achievement triggers without code changes.
