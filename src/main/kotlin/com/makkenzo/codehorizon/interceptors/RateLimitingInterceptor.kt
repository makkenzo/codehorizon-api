package com.makkenzo.codehorizon.interceptors

import com.fasterxml.jackson.databind.ObjectMapper
import com.makkenzo.codehorizon.configs.KeyStrategy
import com.makkenzo.codehorizon.configs.RateLimited
import com.makkenzo.codehorizon.services.UserService
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import io.github.bucket4j.BucketConfiguration
import io.github.bucket4j.Refill
import io.github.bucket4j.distributed.proxy.ProxyManager
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import java.time.Duration
import java.util.function.Supplier

@Component
class RateLimitingInterceptor(
    private val proxyManager: ProxyManager<String>,
    private val userService: UserService,
    private val objectMapper: ObjectMapper
) : HandlerInterceptor {

    private val logger = LoggerFactory.getLogger(RateLimitingInterceptor::class.java)

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (handler !is HandlerMethod) {
            return true
        }

        val rateLimitedAnnotation = handler.getMethodAnnotation(RateLimited::class.java)
            ?: handler.beanType.getAnnotation(RateLimited::class.java)
            ?: return true

        val key = resolveKey(request, rateLimitedAnnotation.strategy, handler)
        val bucketKey = "${rateLimitedAnnotation.keyPrefix}$key"

        val refill =
            Refill.greedy(rateLimitedAnnotation.limit, Duration.ofSeconds(rateLimitedAnnotation.durationSeconds))
        val limit = Bandwidth.classic(rateLimitedAnnotation.limit, refill)

        val configurationSupplier = Supplier<BucketConfiguration> {
            BucketConfiguration.builder()
                .addLimit(limit)
                .build()
        }

        val bucket: Bucket = proxyManager.builder().build(bucketKey, configurationSupplier)

        val probe = bucket.tryConsumeAndReturnRemaining(1)

        if (probe.isConsumed) {
            response.addHeader("X-Rate-Limit-Remaining", probe.remainingTokens.toString())
            response.addHeader("X-Rate-Limit-Limit", rateLimitedAnnotation.limit.toString())

            response.addHeader(
                "X-Rate-Limit-Reset",
                (System.currentTimeMillis() / 1000 + rateLimitedAnnotation.durationSeconds).toString()
            )
            return true
        } else {
            val nanosToWaitForRefill = probe.nanosToWaitForRefill
            val secondsToWaitForRefill = nanosToWaitForRefill / 1_000_000_000.0

            response.addHeader(
                "Retry-After",
                String.format("%.0f", Math.ceil(secondsToWaitForRefill))
            )
            response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.format("%.2f", secondsToWaitForRefill))
            response.addHeader("X-Rate-Limit-Limit", rateLimitedAnnotation.limit.toString())
            response.addHeader("X-Rate-Limit-Remaining", "0")
            response.addHeader(
                "X-Rate-Limit-Reset",
                (System.currentTimeMillis() / 1000 + Math.ceil(secondsToWaitForRefill).toLong()).toString()
            )

            response.status = HttpStatus.TOO_MANY_REQUESTS.value()
            response.contentType = "application/json;charset=UTF-8"

            val errorBody = mapOf(
                "error" to "Too Many Requests",
                "message" to "Вы превысили лимит запросов. Пожалуйста, попробуйте снова через ${
                    String.format(
                        "%.0f",
                        Math.ceil(secondsToWaitForRefill)
                    )
                } секунд."
            )
            response.writer.write(objectMapper.writeValueAsString(errorBody))
            logger.warn(
                "Rate limit exceeded for key: {}, URI: {}, User-Agent: {}",
                bucketKey,
                request.requestURI,
                request.getHeader("User-Agent")
            )
            return false
        }
    }

    private fun resolveKey(request: HttpServletRequest, strategy: KeyStrategy, handlerMethod: HandlerMethod): String {
        val baseKey = when (strategy) {
            KeyStrategy.IP_ADDRESS -> getClientIp(request)
            KeyStrategy.USER_ID -> {
                val authentication = SecurityContextHolder.getContext().authentication
                if (authentication != null && authentication.isAuthenticated && authentication.principal is org.springframework.security.core.userdetails.UserDetails) {
                    val userDetails =
                        authentication.principal as org.springframework.security.core.userdetails.UserDetails
                    userService.findByEmail(userDetails.username)?.id ?: getClientIp(request)
                } else {
                    getClientIp(request)
                }
            }
        }
        val controllerName = handlerMethod.beanType.simpleName
        val methodName = handlerMethod.method.name
        return "$baseKey:$controllerName:$methodName"
    }

    private fun getClientIp(request: HttpServletRequest): String {
        var ipAddress = request.getHeader("X-Forwarded-For")
        if (ipAddress.isNullOrEmpty() || "unknown".equals(ipAddress, ignoreCase = true)) {
            ipAddress = request.getHeader("Proxy-Client-IP")
        }
        if (ipAddress.isNullOrEmpty() || "unknown".equals(ipAddress, ignoreCase = true)) {
            ipAddress = request.getHeader("WL-Proxy-Client-IP")
        }
        if (ipAddress.isNullOrEmpty() || "unknown".equals(ipAddress, ignoreCase = true)) {
            ipAddress = request.getHeader("HTTP_CLIENT_IP")
        }
        if (ipAddress.isNullOrEmpty() || "unknown".equals(ipAddress, ignoreCase = true)) {
            ipAddress = request.getHeader("HTTP_X_FORWARDED_FOR")
        }
        if (ipAddress.isNullOrEmpty() || "unknown".equals(ipAddress, ignoreCase = true)) {
            ipAddress = request.remoteAddr
        }
        return ipAddress?.split(",")?.firstOrNull()?.trim() ?: request.remoteAddr ?: "unknown_ip"
    }
}