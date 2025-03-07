package com.makkenzo.codehorizon.controllers

import com.makkenzo.codehorizon.annotations.CookieAuth
import com.makkenzo.codehorizon.configs.CookieConfig
import com.makkenzo.codehorizon.dtos.*
import com.makkenzo.codehorizon.models.MailActionEnum
import com.makkenzo.codehorizon.models.User
import com.makkenzo.codehorizon.services.EmailService
import com.makkenzo.codehorizon.services.TokenBlacklistService
import com.makkenzo.codehorizon.services.UserService
import com.makkenzo.codehorizon.utils.JwtUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth")
class AuthController(
    private val userService: UserService,
    private val jwtUtils: JwtUtils,
    private val emailService: EmailService,
    private val tokenBlacklistService: TokenBlacklistService,
    private val cookieProperties: CookieConfig
) {
    @PostMapping("/register")
    @Operation(summary = "Регистрация пользователя")
    fun register(@RequestBody request: RegisterRequestDTO): ResponseEntity<MessageResponseDTO> {
        return try {
            val user =
                userService.registerUser(request.username, request.email, request.password, request.confirmPassword)

            emailService.sendVerificationEmail(user, MailActionEnum.REGISTRATION)

            ResponseEntity.ok(MessageResponseDTO("Пользователь успешно зарегистрирован. Пожалуйста, подтвердите ваш email."))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(MessageResponseDTO(e.message!!))
        }
    }

    @PostMapping("/login")
    @Operation(summary = "Аутентификация пользователя")
    fun login(@RequestBody request: LoginRequestDTO): ResponseEntity<Void> {
        val user = userService.authenticateUser(request.login, request.password)
        return if (user != null) {
            val accessToken = jwtUtils.generateAccessToken(user)
            val refreshToken = jwtUtils.generateRefreshToken(user)

            userService.updateRefreshToken(user.email, refreshToken)

            val accessCookie = ResponseCookie.from("access_token", accessToken)
                .httpOnly(true)
                .secure(cookieProperties.secure)
                .path("/")
                .maxAge(60 * 15L) // 15 минут
                .build()

            val refreshCookie = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(cookieProperties.secure)
                .path("/")
                .maxAge(60 * 60 * 24L) // 24 часа
                .build()

            ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .build()
        } else {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "Обновление токена")
    fun refreshToken(
        @CookieValue(
            "refresh_token",
            required = false
        ) refreshToken: String?
    ): ResponseEntity<Void> {
        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
        }

        if (jwtUtils.validateToken(refreshToken)) {
            val email = jwtUtils.getSubjectFromToken(refreshToken)
            val user = userService.findByRefreshToken(refreshToken)
            if (user != null && user.email == email) {
                val newAccessToken = jwtUtils.generateAccessToken(user)
                val newRefreshToken = jwtUtils.generateRefreshToken(user)
                userService.updateRefreshToken(email, newRefreshToken)

                val accessCookie = ResponseCookie.from("access_token", newAccessToken)
                    .httpOnly(false)
                    .secure(cookieProperties.secure)
                    .path("/")
                    .maxAge(60 * 15L) // 15 минут
                    .build()

                val refreshCookie = ResponseCookie.from("refresh_token", newRefreshToken)
                    .httpOnly(false)
                    .secure(cookieProperties.secure)
                    .path("/")
                    .maxAge(60 * 60 * 24L) // 24 часа
                    .build()

                return ResponseEntity.noContent()
                    .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                    .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                    .build()
            }
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
    }

    @GetMapping("/me")
    @Operation(summary = "Получить пользователя", security = [SecurityRequirement(name = "bearerAuth")])
    @CookieAuth
    fun getMe(request: HttpServletRequest): ResponseEntity<User> {
        val token = request.cookies?.find { it.name == "access_token" }?.value
            ?: throw IllegalArgumentException("Access token cookie is missing")
        val userId = jwtUtils.getIdFromToken(token)
        val user = userService.getUserById(userId)
        return ResponseEntity.ok(user)
    }

    @PostMapping("/logout")
    @Operation(summary = "Выход пользователя")
    fun logout(
        @CookieValue("access_token", required = false) accessToken: String?,
        @CookieValue("refresh_token", required = false) refreshToken: String?
    ): ResponseEntity<Void> {
        if (accessToken != null) tokenBlacklistService.blacklistToken(accessToken)
        if (refreshToken != null) tokenBlacklistService.blacklistToken(refreshToken)

        val expiredCookie = ResponseCookie.from("access_token", "")
            .path("/")
            .httpOnly(true)
            .secure(cookieProperties.secure)
            .maxAge(0)
            .build()

        val expiredRefreshCookie = ResponseCookie.from("refresh_token", "")
            .path("/")
            .httpOnly(true)
            .secure(cookieProperties.secure)
            .maxAge(0)
            .build()

        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, expiredCookie.toString())
            .header(HttpHeaders.SET_COOKIE, expiredRefreshCookie.toString())
            .build()
    }

    @PostMapping("/reset-password/check-login")
    @Operation(summary = "Поиск логина для сброса пароля", security = [SecurityRequirement(name = "bearerAuth")])
    fun checkLoginValidity(@RequestBody request: CheckLoginRequestDTO): ResponseEntity<MessageResponseDTO> {
        val user = userService.findByLogin(request.login) ?: return ResponseEntity.notFound().build()

        emailService.sendVerificationEmail(user, MailActionEnum.RESET_PASSWORD)

        return ResponseEntity.ok(MessageResponseDTO("Пользователь с таким логином существует. На почту отправлено письмо для сброса пароля."))
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Сброс пароля", security = [SecurityRequirement(name = "bearerAuth")])
    fun resetPassword(@RequestBody body: ResetPasswordRequestDTO, request: HttpServletRequest): ResponseEntity<User> {
        val header = request.getHeader("Authorization")
            ?: throw IllegalArgumentException("Authorization header is missing")
        val token = header.substring(7).trim()
        val email = jwtUtils.getSubjectFromToken(token)
        val user = userService.findByLogin(email) ?: return ResponseEntity.notFound().build()

        val newUser = userService.resetPassword(user, body.password, body.confirmPassword)

        return ResponseEntity.ok(newUser)
    }
}