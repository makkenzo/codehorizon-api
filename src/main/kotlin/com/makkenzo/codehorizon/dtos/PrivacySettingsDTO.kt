package com.makkenzo.codehorizon.dtos

import com.makkenzo.codehorizon.models.ProfileVisibility


data class UpdatePrivacySettingsRequestDTO(
    val profileVisibility: ProfileVisibility?,
    val showEmailOnProfile: Boolean?,
    val showCoursesInProgressOnProfile: Boolean?,
    val showCompletedCoursesOnProfile: Boolean?,
    val showActivityFeedOnProfile: Boolean?,
    val allowDirectMessages: Boolean?
)