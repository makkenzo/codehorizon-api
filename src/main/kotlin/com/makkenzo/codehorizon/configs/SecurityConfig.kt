package com.makkenzo.codehorizon.configs

import com.makkenzo.codehorizon.filters.JwtAuthenticationFilter
import com.makkenzo.codehorizon.handlers.CustomAccessDeniedHandler
import com.makkenzo.codehorizon.handlers.JsonAuthenticationEntryPoint
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val customAccessDeniedHandler: CustomAccessDeniedHandler,
    private val jsonAuthenticationEntryPoint: JsonAuthenticationEntryPoint
) {
    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }

    @Bean
    fun filterChain(
        http: HttpSecurity, accessDeniedHandler: AccessDeniedHandler,
    ): SecurityFilterChain {
        http.csrf { it.disable() }
            .cors {}
            .sessionManagement { session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { requests ->
                requests
                    .requestMatchers(
                        "/api/auth/**",
                        "/api/verify/**",
                        "/api/courses",
                        "/api/courses/{slug}",
                        "/api/courses/categories",
                        "/api/courses/{courseId}/reviews",
                        "/api/courses/{courseId}/reviews/distribution",
                        "/api/users/{username}/profile",
                        "/api/users/popular-authors",
                        "/api/payments/stripe/webhook",
                        "/api/search",
                        "/api/users/{username}/certificates/public",
                        "/swagger-ui/**", "/v3/api-docs/**",
                        "/actuator/prometheus"
                    ).permitAll()
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    .anyRequest().authenticated()

            }
            .exceptionHandling { exceptions ->
                exceptions
                    .authenticationEntryPoint(jsonAuthenticationEntryPoint)
                    .accessDeniedHandler(customAccessDeniedHandler)
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }
}
