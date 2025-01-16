package com.makkenzo.codehorizon.controllers

import com.makkenzo.codehorizon.dtos.AuthResponseDTO
import com.makkenzo.codehorizon.dtos.LoginRequestDTO
import com.makkenzo.codehorizon.dtos.RefreshTokenRequestDTO
import com.makkenzo.codehorizon.dtos.RegisterRequestDTO
import com.makkenzo.codehorizon.services.UserService
import com.makkenzo.codehorizon.utils.JwtUtils
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val userService: UserService,
    private val jwtUtils: JwtUtils
) {
    @PostMapping("/register")
    fun register(@RequestBody request: RegisterRequestDTO): ResponseEntity<AuthResponseDTO> {
        val user = userService.registerUser(request.username, request.email, request.password)

        val accessToken = jwtUtils.generateAccessToken(user.email)
        val refreshToken = jwtUtils.generateRefreshToken(user.email)

        userService.updateRefreshToken(user.email, refreshToken)

        return ResponseEntity.ok(AuthResponseDTO(accessToken, refreshToken))
    }

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequestDTO): ResponseEntity<AuthResponseDTO> {
        val user = userService.authenticateUser(request.email, request.password)
        return if (user != null) {
            val accessToken = jwtUtils.generateAccessToken(user.email)
            val refreshToken = jwtUtils.generateRefreshToken(user.email)
            userService.updateRefreshToken(user.email, refreshToken)
            ResponseEntity.ok(AuthResponseDTO(accessToken, refreshToken))
        } else {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
    }

    @PostMapping("/refresh-token")
    fun refreshToken(@RequestBody request: RefreshTokenRequestDTO): ResponseEntity<AuthResponseDTO> {
        val refreshToken = request.refreshToken
        if (jwtUtils.validateToken(refreshToken)) {
            val email = jwtUtils.getEmailFromToken(refreshToken)
            val user = userService.findByRefreshToken(refreshToken)
            if (user != null && user.email == email) {
                val newAccessToken = jwtUtils.generateAccessToken(email)
                val newRefreshToken = jwtUtils.generateRefreshToken(email)
                userService.updateRefreshToken(email, newRefreshToken)
                return ResponseEntity.ok(AuthResponseDTO(newAccessToken, newRefreshToken))
            }
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
    }
}