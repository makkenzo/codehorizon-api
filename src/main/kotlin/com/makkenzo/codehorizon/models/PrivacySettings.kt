package com.makkenzo.codehorizon.models

enum class ProfileVisibility {
    PUBLIC,       // Профиль виден всем
    FRIENDS_ONLY, // Видно только друзьям
    PRIVATE       // Профиль приватный
}

data class PrivacySettings(
    val profileVisibility: ProfileVisibility = ProfileVisibility.PUBLIC,
    val showEmail: Boolean = false,
    val showLastSeen: Boolean = false
)
