package com.makkenzo.codehorizon.com.makkenzo.codehorizon.controllers

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
        @RequestParam action: String,
        response: HttpServletResponse
    ): ResponseEntity<Any> {
        if (!jwtUtils.validateToken(token)) {
            return ResponseEntity.badRequest().body("Неверный или истекший токен")
        }

        val tokenAction = jwtUtils.getActionFromToken(token)
        if (tokenAction != action) {
            return ResponseEntity.badRequest().body("Некорректный тип действия")
        }

        val email = jwtUtils.getEmailFromToken(token)

        return when (action) {
            "registration" -> {
                val activated = userService.activateAccount(email)

                val user = userService.findByEmail(email)
                    ?: return ResponseEntity.badRequest().body("Пользователь с email $email не найден")

                val accessToken = jwtUtils.generateAccessToken(user)
                val refreshToken = jwtUtils.generateRefreshToken(user)

                userService.updateRefreshToken(user.email, refreshToken)

                val frontDomainUrl =
                    System.getenv("FRONT_DOMAIN_URL") ?: throw RuntimeException("Missing FRONT_DOMAIN_URL")

                if (activated) {
                    val accessTokenCookie = Cookie("access_token", accessToken).apply {
                        maxAge = 3600
                        isHttpOnly = true
                        secure = true
                        path = "/"
                    }
                    val refreshTokenCookie = Cookie("refresh_token", refreshToken).apply {
                        maxAge = 86400
                        isHttpOnly = true
                        secure = true
                        path = "/"
                    }

                    response.addCookie(accessTokenCookie)
                    response.addCookie(refreshTokenCookie)

                    val redirectUrl = "$frontDomainUrl/verify?status=success"
                    return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, redirectUrl).build()
                } else {
                    ResponseEntity.internalServerError()
                        .body("Ошибка активации аккаунта для пользователя с email $email")
                }
            }

            else -> {
                ResponseEntity.badRequest().body("Неизвестный тип действия: $action")
            }
        }
    }
}