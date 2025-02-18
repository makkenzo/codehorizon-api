package com.makkenzo.codehorizon.com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.models.User
import com.makkenzo.codehorizon.utils.JwtUtils
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service

@Service
class EmailService(
    private val mailSender: JavaMailSender,
    private val jwtUtils: JwtUtils
) {
    private val domainUrl = System.getenv("DOMAIN_URL") ?: throw RuntimeException("Missing DOMAIN_URL")
    private val senderEmail = System.getenv("YANDEX_USERNAME") ?: throw RuntimeException("Missing YANDEX_USERNAME")

    fun sendVerificationEmail(user: User, action: String) {
        val token = jwtUtils.generateVerificationToken(user, action)
        val verificationUrl = "$domainUrl/verify?token=$token&action=$action"

        val message = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(message, true, "UTF-8")

        helper.setFrom(senderEmail)
        helper.setTo(user.email)
        helper.setSubject("Подтверждение действия: $action")
        helper.setText(
            """
            <p>Здравствуйте, ${user.username}!</p>
            <p>Для подтверждения вашего действия (<strong>$action</strong>) перейдите по ссылке ниже:</p>
            <p><a href="$verificationUrl">$verificationUrl</a></p>
            <p>Если вы не выполняли это действие, просто проигнорируйте это письмо.</p>
        """.trimIndent(), true
        )

        mailSender.send(message)
    }
}