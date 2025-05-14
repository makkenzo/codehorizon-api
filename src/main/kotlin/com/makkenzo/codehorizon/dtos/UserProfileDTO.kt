package com.makkenzo.codehorizon.dtos

import java.io.Serializable

data class PublicCourseInfoDTO(
    val id: String,
    val title: String,
    val slug: String,
    val imagePreview: String?,
    val progress: Double?
) : Serializable

data class UserProfileDTO(
    val id: String,
    val username: String,
    val profile: ProfileDTO,
    val coursesInProgress: List<PublicCourseInfoDTO>? = null,
    val completedCoursesCount: Int = 0,
    val createdCourses: List<PublicCourseInfoDTO>? = null,
    val level: Int,
) : Serializable