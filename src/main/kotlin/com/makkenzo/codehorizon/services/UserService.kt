package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.models.User
import com.makkenzo.codehorizon.repositories.UserRepository
import com.makkenzo.codehorizon.utils.JwtUtils
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtUtils: JwtUtils
) {
    fun registerUser(username: String, email: String, password: String): User {
        val user = User(
            username = username,
            email = email,
            passwordHash = passwordEncoder.encode(password)
        )
        return userRepository.save(user)
    }

    fun authenticateUser(email: String, password: String): User? {
        val user = userRepository.findByEmail(email)
        return if (user != null && passwordEncoder.matches(password, user.passwordHash)) user else null
    }

    fun updateRefreshToken(email: String, refreshToken: String) {
        val user = userRepository.findByEmail(email)
        if (user != null) {
            userRepository.save(user.copy(refreshToken = refreshToken))
        }
    }

    fun findByRefreshToken(refreshToken: String): User? {
        return userRepository.findByRefreshToken(refreshToken)
    }
}