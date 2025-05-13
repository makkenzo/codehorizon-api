package com.makkenzo.codehorizon.models

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.io.Serializable
import java.time.Instant

@Document(collection = "users")
data class User(
    @Id val id: String? = null,
    var isVerified: Boolean = false,
    @Indexed(unique = true)
    val username: String,
    @Indexed(unique = true)
    val email: String,
    var passwordHash: String,
    @Indexed
    val refreshToken: String? = null,
    val roles: List<String> = listOf("USER"),
    val createdCourseIds: MutableList<String> = mutableListOf(),
    val wishlistId: String? = null,
    val accountSettings: AccountSettings? = AccountSettings(),
    @Indexed
    val createdAt: Instant = Instant.now(),
    val authorities: List<String>? = null
) : Serializable