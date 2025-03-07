package com.makkenzo.codehorizon.controllers

import com.makkenzo.codehorizon.models.MailActionEnum
import com.makkenzo.codehorizon.services.UserService
import com.makkenzo.codehorizon.utils.JwtUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/verify")
@Tag(name = "Verification")
class VerificationController(
    private val jwtUtils: JwtUtils,
    private val userService: UserService
) {
    @GetMapping
    @Operation(summary = "Подтверждение действия")
    fun verify(
        @RequestParam token: String,
        response: HttpServletResponse
    ): ResponseEntity<Any> {
        if (!jwtUtils.validateToken(token)) {
            return ResponseEntity.badRequest().body("Неверный или истекший токен")
        }

        val tokenAction = jwtUtils.getActionFromToken(token)

        if (tokenAction !in MailActionEnum.entries.toTypedArray()) {
            return ResponseEntity.badRequest().body("invalid action")
        }

        val email = jwtUtils.getSubjectFromToken(token)

        val user = userService.findByEmail(email)
            ?: return ResponseEntity.badRequest().body("Пользователь с email $email не найден")

        return when (tokenAction) {
            MailActionEnum.REGISTRATION -> {
                val activated = userService.activateAccount(email)

                val accessToken = jwtUtils.generateAccessToken(user)
                val refreshToken = jwtUtils.generateRefreshToken(user)

                userService.updateRefreshToken(user.email, refreshToken)

                val frontDomainUrl =
                    System.getenv("FRONT_DOMAIN_URL") ?: throw RuntimeException("Missing FRONT_DOMAIN_URL")

                if (activated) {
                    val accessTokenCookie = Cookie("access_token", accessToken).apply {
                        maxAge = 3600
                        isHttpOnly = false
                        //secure = true
                        path = "/"
                    }
                    val refreshTokenCookie = Cookie("refresh_token", refreshToken).apply {
                        maxAge = 86400
                        isHttpOnly = false
                        //secure = true
                        path = "/"
                    }

                    response.addCookie(accessTokenCookie)
                    response.addCookie(refreshTokenCookie)

                    return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, frontDomainUrl).build()
                } else {
                    ResponseEntity.internalServerError()
                        .body("Ошибка активации аккаунта для пользователя с email $email")
                }
            }

            MailActionEnum.RESET_PASSWORD -> {
                val frontDomainUrl =
                    System.getenv("FRONT_DOMAIN_URL") ?: throw RuntimeException("Missing FRONT_DOMAIN_URL")

                return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, "$frontDomainUrl/reset-password?token=$token&").build()
            }
        }
    }
}