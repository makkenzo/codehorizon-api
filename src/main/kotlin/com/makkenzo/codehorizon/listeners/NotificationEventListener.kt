package com.makkenzo.codehorizon.listeners

import com.makkenzo.codehorizon.events.*
import com.makkenzo.codehorizon.models.NotificationType
import com.makkenzo.codehorizon.models.User
import com.makkenzo.codehorizon.repositories.CourseRepository
import com.makkenzo.codehorizon.repositories.UserRepository
import com.makkenzo.codehorizon.services.EmailService
import com.makkenzo.codehorizon.services.NotificationService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.thymeleaf.context.Context
import org.thymeleaf.spring6.SpringTemplateEngine

@Component
class NotificationEventListener(
    private val userRepository: UserRepository,
    @Value("\${FRONT_DOMAIN_URL}") private val frontDomainUrl: String,
    private val emailService: EmailService,
    private val notificationService: NotificationService,
    private val templateEngine: SpringTemplateEngine,
    private val courseRepository: CourseRepository
) {
    private val logger = LoggerFactory.getLogger(NotificationEventListener::class.java)

    @Async
    @EventListener
    fun handleNewMentorshipApplication(event: NewMentorshipApplicationEvent) {
        logger.info("Обработка события NewMentorshipApplicationEvent для заявки ID: {}", event.applicationId)

        try {
            val adminUsers: List<User> = userRepository.findAll().filter {
                it.roles.contains("ROLE_ADMIN") || it.roles.contains("ADMIN")
            }

            val adminEmails: List<String> = adminUsers.map { it.email }.distinct()

            if (adminEmails.isEmpty()) {
                logger.warn(
                    "Не найдены email адреса администраторов для уведомления о новой заявке {}",
                    event.applicationId
                )
                return
            }

            val subject = "Новая заявка на менторство: ${event.applicantUsername}"
            val applicationLink = "$frontDomainUrl/admin/mentorship-applications"
            adminUsers.forEach { adminUser ->
                val context = Context().apply {
                    setVariable("adminName", adminUser.username)
                    setVariable("applicantUsername", event.applicantUsername)
                    setVariable("applicantEmail", event.applicantEmail)
                    setVariable("applicationLink", applicationLink)
                    setVariable("applicationId", event.applicationId)
                }
                val htmlBody =
                    templateEngine.process("new-mentorship-application-admin-email", context)
                emailService.sendSimpleEmail(
                    adminUser.email,
                    subject,
                    htmlBody
                )
            }

            adminUsers.forEach { admin ->
                notificationService.createNotification(
                    userId = admin.id!!,
                    type = NotificationType.MENTORSHIP_APPLICATION_NEW,
                    message = "Новая заявка на менторство от ${event.applicantUsername}.",
                    link = "/admin/mentorship-applications",
                    relatedEntityId = event.applicationId
                )
            }
            logger.info(
                "Уведомления о новой заявке {} отправлены администраторам: {}",
                event.applicationId,
                adminEmails.distinct()
            )
        } catch (e: Exception) {
            logger.error(
                "Ошибка при отправке уведомления администраторам о новой заявке ${event.applicationId}: ${e.message}",
                e
            )
        }
    }

    @Async
    @EventListener
    fun handleMentorshipApplicationApproved(event: MentorshipApplicationApprovedEvent) {
        val applicantUser = userRepository.findById(event.userId).orElse(null)
        if (applicantUser == null) {
            logger.warn("Пользователь с ID {} не найден для отправки email об одобрении заявки.", event.userId)
            return
        }

        val subject = "Ваша заявка на менторство одобрена!"
        val profileLink = "$frontDomainUrl/me/profile"
        val coursesLink = "$frontDomainUrl/admin/courses/new"

        val context = Context().apply {
            setVariable("username", event.applicantUsername)
            setVariable("profileLink", profileLink)
            setVariable("coursesLink", coursesLink)
        }

        notificationService.createNotification(
            userId = applicantUser.id!!,
            type = NotificationType.MENTORSHIP_APPLICATION_APPROVED,
            message = "Ваша заявка на менторство одобрена!",
            link = coursesLink,
            relatedEntityId = event.applicationId
        )
        emailService.sendConfigurableEmail(
            toUser = applicantUser,
            emailTypeCheck = { prefs -> prefs.emailMentorshipStatusChange },
            subject = subject,
            htmlTemplateName = "mentorship-application-approved-email",
            templateContext = context
        )
    }

    @Async
    @EventListener
    fun handleMentorshipApplicationRejected(event: MentorshipApplicationRejectedEvent) {
        logger.info("Обработка события MentorshipApplicationRejectedEvent для заявки ID: {}", event.applicationId)

        val applicantUser = userRepository.findById(event.userId).orElse(null)
        if (applicantUser == null) {
            logger.warn("Пользователь с ID {} не найден для отправки email об отклонении заявки.", event.userId)
            return
        }

        val subject = "Ваша заявка на менторство отклонена"
        val context = Context().apply {
            setVariable("username", event.applicantUsername)
            setVariable("rejectionReason", event.rejectionReason ?: "Причина не указана.")
        }

        notificationService.createNotification(
            userId = applicantUser.id!!,
            type = NotificationType.MENTORSHIP_APPLICATION_REJECTED,
            message = "Ваша заявка на менторство отклонена",
            relatedEntityId = event.applicationId
        )
        emailService.sendConfigurableEmail(
            toUser = applicantUser,
            emailTypeCheck = { prefs -> prefs.emailMentorshipStatusChange },
            subject = subject,
            htmlTemplateName = "mentorship-application-rejected-email",
            templateContext = context
        )
    }

    @Async
    @EventListener
    fun handleCoursePurchased(event: CoursePurchasedEvent) {
        logger.info(
            "Обработка события CoursePurchasedEvent для пользователя {} и курса {}",
            event.userId,
            event.courseId
        )
        val user = userRepository.findById(event.userId).orElse(null) ?: return

        val context = Context().apply {
            setVariable("username", user.username)
            setVariable("courseTitle", event.courseTitle)
            setVariable("courseLink", "$frontDomainUrl/courses/${event.courseSlug}/learn")
        }
        emailService.sendConfigurableEmail(
            toUser = user,
            emailTypeCheck = { prefs -> prefs.emailCoursePurchaseConfirmation },
            subject = "Спасибо за покупку курса \"${event.courseTitle}\"!",
            htmlTemplateName = "course-purchase-confirmation-email",
            templateContext = context
        )
    }

    @Async
    @EventListener
    fun handleCourseCompleted(event: CourseCompletedEvent) {
        logger.info(
            "Обработка события CourseCompletedEvent для пользователя {} и курса {}",
            event.userId,
            event.courseId
        )
        val user = userRepository.findById(event.userId).orElse(null) ?: return

        val context = Context().apply {
            setVariable("username", user.username)
            setVariable("courseTitle", event.courseTitle)
            setVariable("certificateLink", "$frontDomainUrl/me/certificates")
            setVariable("courseLink", "$frontDomainUrl/courses/${event.courseSlug}")
        }
        emailService.sendConfigurableEmail(
            toUser = user,
            emailTypeCheck = { prefs -> prefs.emailCourseCompletion },
            subject = "Поздравляем с завершением курса \"${event.courseTitle}\"!",
            htmlTemplateName = "course-completion-email",
            templateContext = context
        )

        val course = courseRepository.findById(event.courseId).orElse(null) ?: return
        if (course.authorId != user.id) {
            val author = userRepository.findById(course.authorId).orElse(null) ?: return
            val authorContext = Context().apply {
                setVariable("authorName", author.username)
                setVariable("studentUsername", user.username)
                setVariable("courseTitle", course.title)
                setVariable("progressLink", "$frontDomainUrl/admin/courses/${course.id}/students")
            }
            emailService.sendConfigurableEmail(
                toUser = author,
                emailTypeCheck = { prefs -> prefs.emailStudentCompletedMyCourse },
                subject = "Студент ${user.username} завершил ваш курс \"${course.title}\"",
                htmlTemplateName = "student-completed-your-course-email",
                templateContext = authorContext
            )
        }
    }

    @Async
    @EventListener
    fun handleNewReviewOnCourse(event: NewReviewOnCourseEvent) {
        logger.info(
            "Обработка события NewReviewOnCourseEvent для курса {}, автор отзыва {}",
            event.courseTitle,
            event.reviewAuthorUsername
        )
        val courseAuthor = userRepository.findById(event.courseAuthorId).orElse(null) ?: return

        val context = Context().apply {
            setVariable("authorName", courseAuthor.username)
            setVariable("courseTitle", event.courseTitle)
            setVariable("reviewAuthorUsername", event.reviewAuthorUsername)
            setVariable("reviewsLink", "$frontDomainUrl/courses/${event.courseSlug}#reviews")
        }
        emailService.sendConfigurableEmail(
            toUser = courseAuthor,
            emailTypeCheck = { prefs -> prefs.emailNewReviewOnMyCourse },
            subject = "Новый отзыв на ваш курс \"${event.courseTitle}\"",
            htmlTemplateName = "new-review-on-your-course-email",
            templateContext = context
        )
    }

    @Async
    @EventListener
    fun handleAchievementUnlocked(event: AchievementUnlockedEvent) {
        logger.info(
            "Обработка события AchievementUnlockedEvent для пользователя ID: {}, достижение: {}",
            event.userId,
            event.achievement.name
        )
        try {
            val user = userRepository.findById(event.userId).orElse(null)
            if (user == null) {
                logger.warn("Пользователь с ID {} не найден для отправки уведомления о достижении.", event.userId)
                return
            }

            notificationService.createNotification(
                userId = event.userId,
                type = NotificationType.ACHIEVEMENT_UNLOCKED,
                message = "Поздравляем! Вы получили достижение: \"${event.achievement.name}\"!",
                link = "/me/profile?tab=achievements",
                relatedEntityId = event.achievement.id
            )
            logger.info("Внутрисистемное уведомление о достижении '${event.achievement.name}' создано для пользователя ${user.username}")

            val context = Context().apply {
                setVariable("username", user.username)
                setVariable("achievementName", event.achievement.name)
                setVariable("achievementDescription", event.achievement.description)
                setVariable("achievementsLink", "$frontDomainUrl/me/profile?tab=achievements")
            }

            emailService.sendConfigurableEmail(
                toUser = user,
                emailTypeCheck = { prefs -> prefs.emailAchievementUnlocked },
                subject = "Новое достижение: ${event.achievement.name}!",
                htmlTemplateName = "achievement-unlocked-email",
                templateContext = context
            )
            logger.info("Email уведомление о достижении '${event.achievement.name}' запланировано к отправке пользователю ${user.username}")

        } catch (e: Exception) {
            logger.error(
                "Ошибка при обработке AchievementUnlockedEvent для пользователя ${event.userId}, достижение ${event.achievement.name}: ${e.message}",
                e
            )
        }
    }
}