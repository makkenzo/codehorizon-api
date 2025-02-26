package com.makkenzo.codehorizon.configs

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingResponseWrapper

@Suppress("BASE_CLASS_FIELD_MAY_SHADOW_DERIVED_CLASS_PROPERTY")
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
class RequestResponseLoggingFilter : OncePerRequestFilter() {

    private val logger = LoggerFactory.getLogger(RequestResponseLoggingFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val wrappedResponse = ContentCachingResponseWrapper(response)

        try {
            filterChain.doFilter(request, wrappedResponse)
        } finally {
            wrappedResponse.copyBodyToResponse()
            logger.info("${request.method} ${request.requestURI}: ${wrappedResponse.status}")
        }
    }
}
