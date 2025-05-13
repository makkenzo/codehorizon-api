package com.makkenzo.codehorizon.models

import java.io.Serializable

enum class ProfileVisibility {
    PUBLIC,             // Профиль виден всем
    REGISTERED_USERS,   // Виден только зарегистрированным пользователям
    PRIVATE             // Профиль приватный
}

data class PrivacySettings(
    val profileVisibility: ProfileVisibility = ProfileVisibility.PUBLIC,
    val showEmailOnProfile: Boolean = false,
    val showCoursesInProgressOnProfile: Boolean = true,
    val showCompletedCoursesOnProfile: Boolean = true,
    val showActivityFeedOnProfile: Boolean = true,
    val allowDirectMessages: Boolean = true
) : Serializable