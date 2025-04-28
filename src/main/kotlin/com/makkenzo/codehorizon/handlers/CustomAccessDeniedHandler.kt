package com.makkenzo.codehorizon.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import com.makkenzo.codehorizon.dtos.ErrorResponseDTO
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class CustomAccessDeniedHandler(private val objectMapper: ObjectMapper) : AccessDeniedHandler {
    private val logger = LoggerFactory.getLogger(CustomAccessDeniedHandler::class.java)

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException
    ) {
        logger.warn(
            "Access Denied: User '{}' attempted to access protected resource '{}'. Reason: {}",
            request.userPrincipal?.name ?: "anonymous",
            request.requestURI,
            accessDeniedException.message
        )

        response.status = HttpStatus.FORBIDDEN.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE

        val errorResponse = ErrorResponseDTO(
            timestamp = LocalDateTime.now(),
            status = HttpStatus.FORBIDDEN.value(),
            error = HttpStatus.FORBIDDEN.reasonPhrase,
            message = accessDeniedException.message ?: "Access is denied",
            path = request.requestURI
        )
        
        response.writer.write(objectMapper.writeValueAsString(errorResponse))
    }
}