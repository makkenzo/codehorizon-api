package com.makkenzo.codehorizon.configs

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

@Component
class CustomAuthenticationEntryPoint : AuthenticationEntryPoint {

    private val log: Logger = LoggerFactory.getLogger(CustomAuthenticationEntryPoint::class.java)

    override fun commence(
        request: HttpServletRequest?,
        response: HttpServletResponse?,
        authException: AuthenticationException?
    ) {
        log.warn("Unauthorized access to ${request?.requestURI}, returning 401")

        response?.status = HttpServletResponse.SC_UNAUTHORIZED
        response?.writer?.write("Unauthorized")
    }
}
