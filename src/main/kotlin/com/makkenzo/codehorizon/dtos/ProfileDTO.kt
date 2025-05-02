package com.makkenzo.codehorizon.dtos

import java.io.Serializable

data class ProfileDTO(
    val avatarUrl: String? = null,
    val avatarColor: String? = null,
    val bio: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val location: String? = null,
    val website: String? = null,
) : Serializable