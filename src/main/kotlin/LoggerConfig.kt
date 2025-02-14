package com.makkenzo.codehorizon

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.apache.catalina.filters.AddDefaultCharsetFilter.ResponseWrapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter


@Component
class RequestResponseLoggingFilter : OncePerRequestFilter() {

    private val logger = LoggerFactory.getLogger(RequestResponseLoggingFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // Логирование входящего запроса


        // Оборачиваем response для логирования его кода
        val wrappedResponse = ResponseWrapper(response, "UTF-8");

        try {
            // Пропускаем запрос через цепочку фильтров
            filterChain.doFilter(request, wrappedResponse)
        } finally {
            // Логирование кода ответа
            logger.info("${request.method} ${request.requestURI}: ${wrappedResponse.status}")
        }
    }
}