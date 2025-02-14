package com.makkenzo.codehorizon.models

import com.makkenzo.codehorizon.com.makkenzo.codehorizon.models.CourseProgress
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "users")
data class User(
    @Id val id: String? = null,
    val username: String,
    val email: String,
    val passwordHash: String,
    val refreshToken: String? = null,
    val roles: List<String> = listOf("USER"),
    val courses: MutableList<CourseProgress> = mutableListOf(),
    val created_courses: MutableList<String> = mutableListOf()
)