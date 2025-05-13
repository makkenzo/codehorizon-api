package com.makkenzo.codehorizon.events

import org.springframework.context.ApplicationEvent

data class CoursePurchasedEvent(
    val eventSource: Any,
    val userId: String,
    val userUsername: String,
    val userEmail: String,
    val courseId: String,
    val courseTitle: String,
    val courseSlug: String
) : ApplicationEvent(eventSource)

data class CourseCompletedEvent(
    val eventSource: Any,
    val userId: String,
    val userUsername: String,
    val userEmail: String,
    val courseId: String,
    val courseTitle: String,
    val courseSlug: String,
    val courseAuthorId: String
) : ApplicationEvent(eventSource)

data class NewReviewOnCourseEvent(
    val eventSource: Any,
    val courseAuthorId: String,
    val courseAuthorUsername: String,
    val courseAuthorEmail: String,
    val courseId: String,
    val courseTitle: String,
    val courseSlug: String,
    val reviewAuthorUsername: String
) : ApplicationEvent(eventSource)