package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.models.User
import com.makkenzo.codehorizon.repositories.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.util.regex.Pattern

@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,

    ) {
    fun registerUser(username: String, email: String, password: String, confirmPassword: String): User {
        if (userRepository.findByEmail(email) != null) {
            throw IllegalArgumentException("Email already exists")
        }

        if (userRepository.findByUsername(username) != null) {
            throw IllegalArgumentException("Username already exists")
        }

        if (password != confirmPassword) {
            throw IllegalArgumentException("Passwords do not match")
        }

        validatePassword(password)

        val user = User(
            username = username,
            email = email,
            passwordHash = passwordEncoder.encode(password)
        )
        return userRepository.save(user)
    }

    private fun validatePassword(password: String) {
        if (password.length < 8) {
            throw IllegalArgumentException("Password must be at least 8 characters long")
        }
        val pattern = Pattern.compile("^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=])(?=\\S+$).{8,}$")
        if (!pattern.matcher(password).matches()) {
            throw IllegalArgumentException("Password must contain at least one uppercase letter, one lowercase letter, one number, and one special character")
        }
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