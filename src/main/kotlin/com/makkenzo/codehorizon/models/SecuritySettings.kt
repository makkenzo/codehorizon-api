package com.makkenzo.codehorizon.models

import java.io.Serializable

data class SecuritySettings(
    val twoFactorEnabled: Boolean = false,
    val loginAlerts: Boolean = true
) : Serializable
