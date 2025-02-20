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
class JwtAuthAspect(private val jwtUtils: JwtUtils, private val request: HttpServletRequest) {
    @Before("@annotation(com.makkenzo.codehorizon.annotations.JwtAuth)")
    fun checkJwtAuth() {
        try {
            val token = extractToken(request)
            if (token != null && jwtUtils.validateToken(token)) {
                val email = jwtUtils.getSubjectFromToken(token)
                val authentication = UsernamePasswordAuthenticationToken(email, null, emptyList())
                SecurityContextHolder.getContext().authentication = authentication
            } else {
                throw AuthenticationCredentialsNotFoundException("Invalid or missing token")
            }
        } catch (e: Exception) {
            throw AuthenticationCredentialsNotFoundException("Invalid or missing token")
        }
    }

    private fun extractToken(request: HttpServletRequest): String? {
        val authHeader = request.getHeader("Authorization")
        return if (authHeader != null && authHeader.startsWith("Bearer ")) {
            authHeader.substring(7).trim()
        } else {
            null
        }
    }
}