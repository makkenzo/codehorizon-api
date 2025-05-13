package com.makkenzo.codehorizon.models

import java.io.Serializable

data class NotificationPreferences(
    val emailGlobalOnOff: Boolean = true,
    val emailMentorshipStatusChange: Boolean = true,
    val emailCoursePurchaseConfirmation: Boolean = true,
    val emailCourseCompletion: Boolean = true,
    val emailNewReviewOnMyCourse: Boolean = true,
    val emailStudentCompletedMyCourse: Boolean = true,
    val emailMarketingNewCourses: Boolean = true,
    val emailMarketingUpdates: Boolean = true,
    val emailProgressReminders: Boolean = false,
    val emailSecurityAlerts: Boolean = true,
) : Serializable