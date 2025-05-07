package com.makkenzo.codehorizon.models

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.io.Serializable

@Document(collection = "profiles")
data class Profile(
    @Id val id: String? = null,
    val avatarUrl: String? = null,
    val avatarColor: String? = null,
    val userId: String,
    val bio: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val location: String? = null,
    val website: String? = null,
    var signatureUrl: String? = null,
) : Serializable