package com.makkenzo.codehorizon.utils

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class JwtFilter(private val jwtUtils: JwtUtils) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = extractToken(request)
        if (token != null && jwtUtils.validateToken(token)) {
            val email = jwtUtils.getEmailFromToken(token)
            val authentication = UsernamePasswordAuthenticationToken(email, null, emptyList())
            SecurityContextHolder.getContext().authentication = authentication
        }
        filterChain.doFilter(request, response)
    }

    private fun extractToken(request: HttpServletRequest): String? {
        val header = request.getHeader("Authorization")
        return if (header != null && header.startsWith("Bearer ")) header.substring(7) else null
    }
}