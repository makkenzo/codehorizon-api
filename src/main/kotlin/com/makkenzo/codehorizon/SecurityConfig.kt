package com.makkenzo.codehorizon

import com.makkenzo.codehorizon.utils.JwtFilter
import com.makkenzo.codehorizon.utils.JwtUtils
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(private val jwtUtils: JwtUtils) {
    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http.csrf { it.disable() }
            .authorizeHttpRequests { requests ->
                requests
                    .requestMatchers("/api/auth/**").permitAll()
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/swagger-ui/index.html")
                    .permitAll()
                    .anyRequest().authenticated()
            }
            .addFilterBefore(JwtFilter(jwtUtils), UsernamePasswordAuthenticationFilter::class.java)
            .exceptionHandling { exceptions ->
                exceptions
                    .authenticationEntryPoint { request, response, authException ->
                        response.status = HttpStatus.UNAUTHORIZED.value()
                        response.contentType = "application/json"
                        response.writer.write("{\"error\": \"Unauthorized: ${authException.message}\"}")
                    }
                    .accessDeniedHandler { request, response, accessDeniedException ->
                        response.status = HttpStatus.FORBIDDEN.value()
                        response.contentType = "application/json"
                        response.writer.write("{\"error\": \"Forbidden: ${accessDeniedException.message}\"}")
                    }
            }
        return http.build()
    }
}