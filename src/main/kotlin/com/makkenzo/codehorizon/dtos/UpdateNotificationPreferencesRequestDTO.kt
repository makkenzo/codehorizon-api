package com.makkenzo.codehorizon.dtos

data class UpdateNotificationPreferencesRequestDTO(
    val emailGlobalOnOff: Boolean?,
    val emailMentorshipStatusChange: Boolean?,
    val emailCoursePurchaseConfirmation: Boolean?,
    val emailCourseCompletion: Boolean?,
    val emailNewReviewOnMyCourse: Boolean?,
    val emailStudentCompletedMyCourse: Boolean?,
    val emailMarketingNewCourses: Boolean?,
    val emailMarketingUpdates: Boolean?,
    val emailProgressReminders: Boolean?,
    val emailSecurityAlerts: Boolean?
)
