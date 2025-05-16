package com.makkenzo.codehorizon.configs

import com.makkenzo.codehorizon.interceptors.RateLimitingInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(private val rateLimitingInterceptor: RateLimitingInterceptor) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(rateLimitingInterceptor)
            .addPathPatterns("/api/**")
    }
}