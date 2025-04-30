package com.makkenzo.codehorizon.models

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "users")
data class User(
    @Id val id: String? = null,
    var isVerified: Boolean = false,
    val username: String,
    val email: String,
    var passwordHash: String,
    val refreshToken: String? = null,
    val roles: List<String> = listOf("USER"),
    val createdCourseIds: MutableList<String> = mutableListOf(),
    val wishlistId: String? = null,
    val accountSettings: AccountSettings? = null,
    val createdAt: Instant = Instant.now()
)