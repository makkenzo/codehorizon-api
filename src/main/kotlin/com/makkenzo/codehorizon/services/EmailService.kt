package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.models.MailActionEnum
import com.makkenzo.codehorizon.models.NotificationPreferences
import com.makkenzo.codehorizon.models.User
import com.makkenzo.codehorizon.utils.JwtUtils
import org.slf4j.LoggerFactory
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context

@Service
class EmailService(
    private val mailSender: JavaMailSender,
    private val jwtUtils: JwtUtils,
    private val templateEngine: TemplateEngine
) {
    private val domainUrl = System.getenv("DOMAIN_URL") ?: throw RuntimeException("Missing DOMAIN_URL")
    private val senderEmail = System.getenv("SMTP_USERNAME") ?: throw RuntimeException("Missing SMTP_USERNAME")
    private val logger = LoggerFactory.getLogger(EmailService::class.java)

    fun sendVerificationEmail(user: User, action: MailActionEnum) {
        val token = jwtUtils.generateVerificationToken(user, action)
        val verificationUrl = "$domainUrl/verify?token=$token&action=$action"

        val context = Context().apply {
            setVariable("username", user.username)
            setVariable("action", action)
            setVariable("verificationUrl", verificationUrl)
        }

        val htmlContent = when (action) {
            MailActionEnum.REGISTRATION -> {
                templateEngine.process("verification-email", context)
            }

            MailActionEnum.RESET_PASSWORD -> {
                templateEngine.process("reset-password-email", context)
            }
        }

        val message = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(message, true, "UTF-8")

        val actionDescription = when (action) {
            MailActionEnum.REGISTRATION -> "Регистрация"
            MailActionEnum.RESET_PASSWORD -> "Сброс пароля"
        }

        helper.setFrom(senderEmail)
        helper.setTo(user.email)
        helper.setSubject("Подтверждение действия: $actionDescription")
        helper.setText(
            htmlContent, true
        )

        mailSender.send(message)
    }

    fun sendConfigurableEmail(
        toUser: User,
        emailTypeCheck: (NotificationPreferences) -> Boolean,
        subject: String,
        htmlTemplateName: String,
        templateContext: Context,
        isSecurityAlert: Boolean = false
    ) {
        val userNotifPrefs = toUser.accountSettings?.notificationPreferences ?: NotificationPreferences()

        if (!userNotifPrefs.emailGlobalOnOff) {
            if (isSecurityAlert && userNotifPrefs.emailSecurityAlerts) {
                logger.info(
                    "Глобальная отправка email для пользователя {} отключена, но это оповещение безопасности.",
                    toUser.email
                )
            } else {
                return
            }
        }

        if (isSecurityAlert && !userNotifPrefs.emailSecurityAlerts) {
            return
        }

        if (!isSecurityAlert && !emailTypeCheck(userNotifPrefs)) {
            return
        }

        try {
            val htmlBody = templateEngine.process(htmlTemplateName, templateContext)

            val message = mailSender.createMimeMessage()
            val helper = MimeMessageHelper(message, true, "UTF-8")
            helper.setFrom(senderEmail)
            helper.setTo(toUser.email)
            helper.setSubject(subject)
            helper.setText(htmlBody, true)
            mailSender.send(message)
        } catch (e: Exception) {
            logger.error("Ошибка отправки email '{}' пользователю {}: {}", subject, toUser.email, e.message)
        }
    }

    fun sendSimpleEmail(to: String, subject: String, text: String) {
        try {
            val message = mailSender.createMimeMessage()
            val helper = MimeMessageHelper(message, true, "UTF-8")
            helper.setFrom(senderEmail)
            helper.setTo(to)
            helper.setSubject(subject)
            helper.setText(text, true)
            mailSender.send(message)
        } catch (e: Exception) {
            logger.error("Ошибка отправки простого письма '{}' на {}: {}", subject, to, e.message)
        }
    }
}