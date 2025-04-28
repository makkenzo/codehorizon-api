package com.makkenzo.codehorizon.configs

import com.makkenzo.codehorizon.filters.JwtAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val customAuthenticationEntryPoint: CustomAuthenticationEntryPoint
) {
    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }

    @Bean
    fun filterChain(
        http: HttpSecurity,
    ): SecurityFilterChain {
        http.csrf { it.disable() }
            .cors {}
            .sessionManagement { session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { requests ->
                requests
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    .requestMatchers(
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/auth/refresh-token",
                        "/api/auth/reset-password/**",
                        "/api/verify",
                        "/api/courses",
                        "/api/courses/{slug}",
                        "/api/users/{username}/profile",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/actuator/**"
                    ).permitAll()
                    .anyRequest().authenticated()
            }
            .exceptionHandling { exceptions ->
                exceptions
                    .authenticationEntryPoint(customAuthenticationEntryPoint)
                    .accessDeniedHandler { request, response, accessDeniedException ->
                        response.status = HttpStatus.FORBIDDEN.value()
                        response.contentType = "application/json"
                        response.writer.write("{\"error\": \"Forbidden: ${accessDeniedException.message}\"}")
                    }
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }
}
