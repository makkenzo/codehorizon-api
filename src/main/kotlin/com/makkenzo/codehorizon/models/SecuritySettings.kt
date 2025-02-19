package com.makkenzo.codehorizon.models

data class SecuritySettings(
    val twoFactorEnabled: Boolean = false,
    val loginAlerts: Boolean = true
)
