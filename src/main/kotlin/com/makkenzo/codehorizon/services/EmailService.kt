package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.models.MailActionEnum
import com.makkenzo.codehorizon.models.User
import com.makkenzo.codehorizon.utils.JwtUtils
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
}