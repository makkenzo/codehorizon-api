package com.makkenzo.codehorizon.dtos

import com.makkenzo.codehorizon.models.AchievementRarity
import com.makkenzo.codehorizon.models.AchievementTriggerType
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.URL

data class AdminCreateAchievementDTO(
    @field:NotBlank(message = "Ключ не может быть пустым")
    @field:Size(min = 3, max = 100, message = "Длина ключа должна быть от 3 до 100 символов")
    val key: String,

    @field:NotBlank(message = "Название не может быть пустым")
    @field:Size(max = 255, message = "Длина названия не должна превышать 255 символов")
    val name: String,

    @field:NotBlank(message = "Описание не может быть пустым")
    @field:Size(max = 1000, message = "Длина описания не должна превышать 1000 символов")
    val description: String,

    @field:NotBlank(message = "URL иконки не может быть пустым")
    @field:URL(message = "Некорректный URL иконки")
    val iconUrl: String,

    val triggerType: AchievementTriggerType,

    @field:Min(value = 0, message = "Порог срабатывания не может быть отрицательным")
    val triggerThreshold: Int,

    @field:Size(max = 255, message = "Длина значения порога не должна превышать 255 символов")
    val triggerThresholdValue: String? = null,

    @field:PositiveOrZero(message = "XP бонус не может быть отрицательным")
    val xpBonus: Long = 0L,

    val rarity: AchievementRarity = AchievementRarity.COMMON,
    val isGlobal: Boolean = true,

    @field:Min(value = 0, message = "Порядок не может быть отрицательным")
    val order: Int = 0,

    @field:Size(max = 100, message = "Длина категории не должна превышать 100 символов")
    val category: String? = null,

    val isHidden: Boolean = false,
    val prerequisites: List<@NotBlank(message = "Ключ предусловия не может быть пустым") String> = emptyList()
)

data class AdminUpdateAchievementDTO(
    @field:Size(min = 3, max = 100, message = "Длина ключа должна быть от 3 до 100 символов")
    val key: String? = null,

    @field:Size(max = 255, message = "Длина названия не должна превышать 255 символов")
    val name: String? = null,

    @field:Size(max = 1000, message = "Длина описания не должна превышать 1000 символов")
    val description: String? = null,

    @field:URL(message = "Некорректный URL иконки")
    val iconUrl: String? = null,

    val triggerType: AchievementTriggerType? = null,

    @field:Min(value = 0, message = "Порог срабатывания не может быть отрицательным")
    val triggerThreshold: Int? = null,

    @field:Size(max = 255, message = "Длина значения порога не должна превышать 255 символов")
    val triggerThresholdValue: String? = null,

    @field:PositiveOrZero(message = "XP бонус не может быть отрицательным")
    val xpBonus: Long? = null,

    val rarity: AchievementRarity? = null,
    val isGlobal: Boolean? = null,

    @field:Min(value = 0, message = "Порядок не может быть отрицательным")
    val order: Int? = null,

    @field:Size(max = 100, message = "Длина категории не должна превышать 100 символов")
    val category: String? = null,

    val isHidden: Boolean? = null,
    val prerequisites: List<String>? = null
)