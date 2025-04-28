package com.makkenzo.codehorizon.handlers

import com.makkenzo.codehorizon.dtos.ErrorResponseDTO
import com.makkenzo.codehorizon.exceptions.NotFoundException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestCookieException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

@RestControllerAdvice
class GlobalExceptionHandler {
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFoundException(ex: NotFoundException, request: HttpServletRequest): ResponseEntity<ErrorResponseDTO> {
        logger.warn("Resource not found: {} at path {}", ex.message, request.requestURI)
        val errorResponse = ErrorResponseDTO(
            status = HttpStatus.NOT_FOUND.value(),
            error = HttpStatus.NOT_FOUND.reasonPhrase,
            message = ex.message ?: "Resource not found",
            path = request.requestURI
        )
        return ResponseEntity(errorResponse, HttpStatus.NOT_FOUND)
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(
        ex: ResponseStatusException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponseDTO> {
        logger.warn("ResponseStatusException: {} (Status: {}) at path {}", ex.reason, ex.statusCode, request.requestURI)
        val status = HttpStatus.valueOf(ex.statusCode.value())
        val errorResponse = ErrorResponseDTO(
            status = status.value(),
            error = status.reasonPhrase,
            message = ex.reason ?: "Request failed",
            path = request.requestURI
        )
        return ResponseEntity(errorResponse, status)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationExceptions(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponseDTO> {
        val errors = ex.bindingResult.fieldErrors.joinToString(", ") { "'${it.field}': ${it.defaultMessage}" }
        val message = "Validation failed: $errors"
        logger.warn("Validation error: {} at path {}", message, request.requestURI)
        val errorResponse = ErrorResponseDTO(
            status = HttpStatus.BAD_REQUEST.value(),
            error = HttpStatus.BAD_REQUEST.reasonPhrase,
            message = message,
            path = request.requestURI
        )
        return ResponseEntity(errorResponse, HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(
        ex: IllegalArgumentException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponseDTO> {
        logger.warn("Illegal argument: {} at path {}", ex.message, request.requestURI)
        val errorResponse = ErrorResponseDTO(
            status = HttpStatus.BAD_REQUEST.value(),
            error = HttpStatus.BAD_REQUEST.reasonPhrase,
            message = ex.message ?: "Invalid argument provided",
            path = request.requestURI
        )
        return ResponseEntity(errorResponse, HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthenticationException(
        ex: AuthenticationException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponseDTO> {
        logger.warn("Authentication failure: {} at path {}", ex.message, request.requestURI)
        val errorResponse = ErrorResponseDTO(
            status = HttpStatus.UNAUTHORIZED.value(),
            error = HttpStatus.UNAUTHORIZED.reasonPhrase,
            message = ex.message ?: "Authentication required",
            path = request.requestURI
        )
        return ResponseEntity(errorResponse, HttpStatus.UNAUTHORIZED)
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(
        ex: AccessDeniedException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponseDTO> {
        logger.warn("Access denied: {} at path {}", ex.message, request.requestURI)
        val errorResponse = ErrorResponseDTO(
            status = HttpStatus.FORBIDDEN.value(),
            error = HttpStatus.FORBIDDEN.reasonPhrase,
            message = ex.message ?: "Access is denied",
            path = request.requestURI
        )
        return ResponseEntity(errorResponse, HttpStatus.FORBIDDEN)
    }

    @ExceptionHandler(MissingRequestCookieException::class)
    fun handleMissingCookieException(
        ex: MissingRequestCookieException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponseDTO> {
        val message = "Required cookie '${ex.cookieName}' is missing."
        logger.warn("{} Path: {}", message, request.requestURI)

        val status = HttpStatus.BAD_REQUEST

        val errorResponse = ErrorResponseDTO(
            status = status.value(),
            error = status.reasonPhrase,
            message = message,
            path = request.requestURI
        )
        return ResponseEntity(errorResponse, status)
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponseDTO> {
        logger.error("Unexpected error occurred at path {}", request.requestURI, ex)
        val errorResponse = ErrorResponseDTO(
            status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            error = HttpStatus.INTERNAL_SERVER_ERROR.reasonPhrase,
            message = "An unexpected internal server error occurred. Please try again later.",
            path = request.requestURI
        )
        return ResponseEntity(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR)
    }
}