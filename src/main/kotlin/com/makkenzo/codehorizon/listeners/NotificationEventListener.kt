package com.makkenzo.codehorizon.listeners

import com.makkenzo.codehorizon.events.MentorshipApplicationApprovedEvent
import com.makkenzo.codehorizon.events.MentorshipApplicationRejectedEvent
import com.makkenzo.codehorizon.events.NewMentorshipApplicationEvent
import com.makkenzo.codehorizon.models.NotificationType
import com.makkenzo.codehorizon.models.User
import com.makkenzo.codehorizon.repositories.UserRepository
import com.makkenzo.codehorizon.services.EmailService
import com.makkenzo.codehorizon.services.NotificationService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class NotificationEventListener(
    private val userRepository: UserRepository,
    @Value("\${FRONT_DOMAIN_URL}") private val frontDomainUrl: String,
    private val emailService: EmailService,
    private val notificationService: NotificationService
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
            val text = """
                Здравствуйте, Администратор!
                
                Поступила новая заявка на менторство от пользователя ${event.applicantUsername} (${event.applicantEmail}).
                Пожалуйста, рассмотрите ее в панели администратора: $applicationLink
                
                ID заявки: ${event.applicationId}
                
                С уважением,
                Платформа CodeHorizon
            """.trimIndent()

            adminEmails.forEach { adminEmail ->
                try {
                    emailService.sendSimpleEmail(adminEmail, subject, text)
                } catch (e: Exception) {
                    logger.error(
                        "Ошибка при отправке email администратору $adminEmail для заявки ${event.applicationId}: ${e.message}",
                        e
                    )
                }
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
        logger.info("Обработка события MentorshipApplicationApprovedEvent для заявки ID: {}", event.applicationId)
        try {
            val subject = "Ваша заявка на менторство одобрена!"
            val profileLink = "$frontDomainUrl/me/profile"
            val coursesLink = "$frontDomainUrl/admin/courses/new"
            val text = """
            Здравствуйте, ${event.applicantUsername}!
            
            Поздравляем! Ваша заявка на получение статуса ментора на платформе CodeHorizon была одобрена.
            Теперь вы можете создавать и публиковать свои курсы.
            
            Не забудьте загрузить свою подпись в профиле для автоматической генерации сертификатов для ваших студентов: $profileLink
            Начать создание курса: $coursesLink
            
            С уважением,
            Команда CodeHorizon
        """.trimIndent()
            emailService.sendSimpleEmail(event.applicantEmail, subject, text)
            logger.info(
                "Уведомление об одобрении заявки {} отправлено пользователю {}",
                event.applicationId,
                event.applicantEmail
            )
        } catch (e: Exception) {
            logger.error(
                "Ошибка при отправке уведомления пользователю об одобрении заявки ${event.applicationId}: ${e.message}",
                e
            )
        }
    }

    @Async
    @EventListener
    fun handleMentorshipApplicationRejected(event: MentorshipApplicationRejectedEvent) {
        logger.info("Обработка события MentorshipApplicationRejectedEvent для заявки ID: {}", event.applicationId)
        try {
            val subject = "Ваша заявка на менторство отклонена"
            var text = """
                Здравствуйте, ${event.applicantUsername}.
                
                Сожалеем, но ваша заявка на получение статуса ментора на платформе CodeHorizon была отклонена.
            """.trimIndent()

            if (!event.rejectionReason.isNullOrBlank()) {
                text += "\n\nПричина отклонения: ${event.rejectionReason}"
            }

            text += """
                
                Если у вас есть вопросы, пожалуйста, свяжитесь с поддержкой.
                
                С уважением,
                Команда CodeHorizon
            """.trimIndent()
            emailService.sendSimpleEmail(event.applicantEmail, subject, text)
            logger.info(
                "Уведомление об отклонении заявки {} отправлено пользователю {}",
                event.applicationId,
                event.applicantEmail
            )
        } catch (e: Exception) {
            logger.error(
                "Ошибка при отправке уведомления пользователю об отклонении заявки ${event.applicationId}: ${e.message}",
                e
            )
        }
    }
}