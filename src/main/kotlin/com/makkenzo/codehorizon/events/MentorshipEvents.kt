package com.makkenzo.codehorizon.events

import org.springframework.context.ApplicationEvent

data class NewMentorshipApplicationEvent(
    val eventSource: Any,
    val applicationId: String,
    val applicantUsername: String,
    val applicantEmail: String
) : ApplicationEvent(eventSource)

data class MentorshipApplicationApprovedEvent(
    val eventSource: Any,
    val applicationId: String,
    val userId: String,
    val applicantUsername: String,
    val applicantEmail: String
) : ApplicationEvent(eventSource)

data class MentorshipApplicationRejectedEvent(
    val eventSource: Any,
    val applicationId: String,
    val userId: String,
    val applicantUsername: String,
    val applicantEmail: String,
    val rejectionReason: String?
) : ApplicationEvent(eventSource)