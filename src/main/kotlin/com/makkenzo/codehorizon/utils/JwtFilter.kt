package com.makkenzo.codehorizon.utils

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class JwtFilter(private val jwtUtils: JwtUtils) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // Игнорируем запросы к Swagger UI, OpenAPI и эндпоинтам аутентификации
        if (request.requestURI.startsWith("/v3/api-docs") ||
            request.requestURI.startsWith("/swagger-ui") ||
            request.requestURI.startsWith("/api/auth")
        ) {
            filterChain.doFilter(request, response)
            return
        }

        val token = extractToken(request)
        if (token != null && jwtUtils.validateToken(token)) {
            val email = jwtUtils.getEmailFromToken(token)
            // Здесь можно добавить логику для загрузки пользователя из базы данных
            val authentication = UsernamePasswordAuthenticationToken(email, null, emptyList())
            SecurityContextHolder.getContext().authentication = authentication
        } else {
            // Если токен отсутствует или невалиден, выбрасываем AuthenticationException
            throw AuthenticationCredentialsNotFoundException("Invalid or missing token")
        }
        filterChain.doFilter(request, response)
    }

    private fun extractToken(request: HttpServletRequest): String? {
        val header = request.getHeader("Authorization")
        return if (header != null && header.startsWith("Bearer ")) header.substring(7) else null
    }
}