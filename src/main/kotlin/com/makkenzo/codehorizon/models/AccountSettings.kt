package com.makkenzo.codehorizon.models

import java.io.Serializable

data class AccountSettings(
    val timeZone: String = "Europe/Moscow",
    val dateFormat: String = "dd.MM.yyyy",
    val notificationPreferences: NotificationPreferences = NotificationPreferences(),
    val privacySettings: PrivacySettings = PrivacySettings(),
    val securitySettings: SecuritySettings = SecuritySettings()
) : Serializable