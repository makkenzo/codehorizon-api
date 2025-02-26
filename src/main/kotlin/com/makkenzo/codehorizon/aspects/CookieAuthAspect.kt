package com.makkenzo.codehorizon.aspects

import com.makkenzo.codehorizon.utils.JwtUtils
import jakarta.servlet.http.HttpServletRequest
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Aspect
@Component
class CookieAuthAspect(
    private val jwtUtils: JwtUtils,
    private val request: HttpServletRequest
) {

    @Before("@annotation(com.makkenzo.codehorizon.annotations.CookieAuth)")
    fun checkCookieAuth() {
        try {
            val tokens = extractTokens(request)
            if (tokens != null && jwtUtils.validateToken(tokens.first)) {
                val email = jwtUtils.getSubjectFromToken(tokens.first)
                val authentication = UsernamePasswordAuthenticationToken(email, null, emptyList())
                SecurityContextHolder.getContext().authentication = authentication
            } else {
                throw AuthenticationCredentialsNotFoundException("Invalid or missing tokens")
            }
        } catch (e: Exception) {
            throw AuthenticationCredentialsNotFoundException("Invalid or missing tokens")
        }
    }

    private fun extractTokens(request: HttpServletRequest): Pair<String, String>? {
        val cookies = request.cookies ?: return null
        val accessToken = cookies.find { it.name == "access_token" }?.value
        val refreshToken = cookies.find { it.name == "refresh_token" }?.value
        return if (accessToken != null && refreshToken != null) {
            Pair(accessToken, refreshToken)
        } else {
            null
        }
    }
}
