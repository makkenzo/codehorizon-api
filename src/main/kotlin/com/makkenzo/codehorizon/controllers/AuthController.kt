package com.makkenzo.codehorizon.controllers

import com.makkenzo.codehorizon.annotations.JwtAuth
import com.makkenzo.codehorizon.com.makkenzo.codehorizon.services.EmailService
import com.makkenzo.codehorizon.dtos.AuthResponseDTO
import com.makkenzo.codehorizon.dtos.LoginRequestDTO
import com.makkenzo.codehorizon.dtos.RefreshTokenRequestDTO
import com.makkenzo.codehorizon.dtos.RegisterRequestDTO
import com.makkenzo.codehorizon.models.User
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
    private val tokenBlacklistService: TokenBlacklistService
) {
    @PostMapping("/register")
    @Operation(summary = "Регистрация пользователя")
    fun register(@RequestBody request: RegisterRequestDTO): ResponseEntity<Any> {
        return try {
            val user =
                userService.registerUser(request.username, request.email, request.password, request.confirmPassword)

            emailService.sendVerificationEmail(user, "registration")

            ResponseEntity.ok("Пользователь успешно зарегистрирован. Пожалуйста, подтвердите ваш email.")
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to e.message))
        }
    }

    @PostMapping("/login")
    @Operation(summary = "Аутентификация пользователя")
    fun login(@RequestBody request: LoginRequestDTO): ResponseEntity<AuthResponseDTO> {
        val user = userService.authenticateUser(request.login, request.password)
        return if (user != null) {
            val accessToken = jwtUtils.generateAccessToken(user)
            val refreshToken = jwtUtils.generateRefreshToken(user)

            userService.updateRefreshToken(user.email, refreshToken)
            ResponseEntity.ok(AuthResponseDTO(accessToken, refreshToken))
        } else {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "Обновление токена")
    fun refreshToken(@RequestBody request: RefreshTokenRequestDTO): ResponseEntity<AuthResponseDTO> {
        val refreshToken = request.refreshToken
        if (jwtUtils.validateToken(refreshToken)) {
            val email = jwtUtils.getEmailFromToken(refreshToken)
            val user = userService.findByRefreshToken(refreshToken)
            if (user != null && user.email == email) {
                val newAccessToken = jwtUtils.generateAccessToken(user)
                val newRefreshToken = jwtUtils.generateRefreshToken(user)
                userService.updateRefreshToken(email, newRefreshToken)
                return ResponseEntity.ok(AuthResponseDTO(newAccessToken, newRefreshToken))
            }
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
    }

    @GetMapping("/token")
    @Operation(summary = "Извлекает токены из печеньки")
    fun getAccessToken(
        @CookieValue(
            "access_token",
            required = false
        ) accessToken: String?,
        @CookieValue(
            "refresh_token",
            required = false
        ) refreshToken: String?
    ): ResponseEntity<Any> {
        return if (accessToken != null && jwtUtils.validateToken(accessToken) && refreshToken != null) {
            ResponseEntity.ok(mapOf("accessToken" to accessToken, "refreshToken" to refreshToken))
        } else {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Wrong token!"))
        }
    }

    @GetMapping("/me")
    @Operation(summary = "Получить пользователя", security = [SecurityRequirement(name = "bearerAuth")])
    @JwtAuth
    fun getMe(request: HttpServletRequest): ResponseEntity<User> {
        val token = request.getHeader("Authorization")
            ?: throw IllegalArgumentException("Authorization header is missing")
        val userId = jwtUtils.getIdFromToken(token.substring(7).trim())
        val user = userService.getUserById(userId)
        return ResponseEntity.ok(user)
    }

    @PostMapping("/logout")
    @Operation(summary = "Выход пользователя")
    fun logout(
        @CookieValue("access_token", required = false) accessToken: String?,
        @CookieValue("refresh_token", required = false) refreshToken: String?
    ): ResponseEntity<Any> {
        if (accessToken != null) tokenBlacklistService.blacklistToken(accessToken)
        if (refreshToken != null) tokenBlacklistService.blacklistToken(refreshToken)

        val expiredCookie = ResponseCookie.from("access_token", "")
            .path("/")
            .httpOnly(true)
            .maxAge(0)
            .build()

        val expiredRefreshCookie = ResponseCookie.from("refresh_token", "")
            .path("/")
            .httpOnly(true)
            .maxAge(0)
            .build()

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, expiredCookie.toString())
            .header(HttpHeaders.SET_COOKIE, expiredRefreshCookie.toString())
            .body(mapOf("message" to "Вы успешно вышли"))
    }
}