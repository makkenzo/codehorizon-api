package com.makkenzo.codehorizon.filters

import com.makkenzo.codehorizon.utils.JwtUtils
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtUtils: JwtUtils,
    private val userDetailsService: UserDetailsService
) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val accessToken = extractTokenFromCookie(request)

        if (accessToken == null) {
            filterChain.doFilter(request, response)
            return
        }

        if (!jwtUtils.validateToken(accessToken)) {
            log.warn("Invalid or expired access token found for URI: {}", request.requestURI)
            filterChain.doFilter(request, response)
            return
        }

        if (SecurityContextHolder.getContext().authentication == null) {
            try {
                val email = jwtUtils.getSubjectFromToken(accessToken)

                val userDetails = userDetailsService.loadUserByUsername(email)

                val authentication = UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.authorities
                )
                authentication.details = WebAuthenticationDetailsSource().buildDetails(request)

                SecurityContextHolder.getContext().authentication = authentication
                log.info(
                    "Successfully authenticated user '{}' with authorities {} for URI: {}",
                    email,
                    userDetails.authorities,
                    request.requestURI
                )
            } catch (e: Exception) {
                log.error("Could not set user authentication in security context for URI: {}", request.requestURI, e)
                SecurityContextHolder.clearContext()
            }
        }

        filterChain.doFilter(request, response)
    }

    private fun extractTokenFromCookie(request: HttpServletRequest): String? {
        return request.cookies?.find { it.name == "access_token" }?.value
    }
}