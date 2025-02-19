package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.models.Profile
import com.makkenzo.codehorizon.models.User
import com.makkenzo.codehorizon.repositories.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.util.regex.Pattern

@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val profileService: ProfileService,
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
        val savedUser = userRepository.save(user)

        val profile = Profile(
            userId = savedUser.id!!,
        )
        profileService.createProfile(profile)

        return savedUser
    }

    fun activateAccount(email: String): Boolean {
        val user = userRepository.findByEmail(email) ?: return false

        if (user.isVerified) {
            return true
        }

        user.isVerified = true
        userRepository.save(user)
        return true
    }

    private fun validatePassword(password: String) {
        if (password.length < 8) {
            throw IllegalArgumentException("Password must be at least 8 characters long")
        }
        val pattern = Pattern.compile("^(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]).{8,}\$")
        if (!pattern.matcher(password).matches()) {
            throw IllegalArgumentException("Password must contain at least one uppercase letter, one lowercase letter, one number, and one special character")
        }
    }

    fun authenticateUser(login: String, password: String): User? {
        val user = userRepository.findByUsernameOrEmail(login, login)
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

    fun findById(id: String): User? {
        return userRepository.findById(id).orElse(null)
    }

    fun findByEmail(email: String): User? {
        return userRepository.findByEmail(email)
    }
}