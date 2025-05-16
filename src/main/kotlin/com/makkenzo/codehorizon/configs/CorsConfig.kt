package com.makkenzo.codehorizon.configs

import com.makkenzo.codehorizon.interceptors.RateLimitingInterceptor
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class CorsConfig(
    private val rateLimitingInterceptor: RateLimitingInterceptor
) : WebMvcConfigurer {
    @Value("\${cors.originPatterns}")
    private val corsOriginPatterns: String = ""

    override fun addCorsMappings(registry: CorsRegistry) {
        val allowedOrigins = corsOriginPatterns.split(",").toTypedArray()
        registry.addMapping("/api/**")
            .allowedOriginPatterns(*allowedOrigins)
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
    }

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(rateLimitingInterceptor)
            .addPathPatterns("/api/**")
    }
}
