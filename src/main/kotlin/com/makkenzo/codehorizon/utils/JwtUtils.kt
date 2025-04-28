package com.makkenzo.codehorizon.utils

import com.makkenzo.codehorizon.models.MailActionEnum
import com.makkenzo.codehorizon.models.User
import com.makkenzo.codehorizon.services.TokenBlacklistService
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.security.SignatureException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.*
import javax.crypto.SecretKey

@Component
class JwtUtils(private val tokenBlacklistService: TokenBlacklistService) {
    private val secretKey: SecretKey = Keys.secretKeyFor(SignatureAlgorithm.HS256)
    private val accessTokenExpirationMs = 900_000 // 15 minutes
    private val refreshTokenExpirationMs = 604_800_000 // 1 day
    private val verificationTokenExpirationMs = 1_800_000 // 30 minutes
    private val logger = LoggerFactory.getLogger(JwtUtils::class.java)

    fun generateAccessToken(user: User): String {
        return Jwts.builder()
            .setSubject(user.email)
            .claim("id", user.id)
            .claim("username", user.username)
            .claim("roles", user.roles)
            .setIssuedAt(Date())
            .setExpiration(Date(System.currentTimeMillis() + accessTokenExpirationMs)).signWith(secretKey).compact()
    }

    fun generateRefreshToken(user: User): String {
        return Jwts.builder()
            .setSubject(user.email)
            .claim("id", user.id)
            .claim("username", user.username)
            .claim("roles", user.roles)
            .setIssuedAt(Date())
            .setExpiration(Date(System.currentTimeMillis() + refreshTokenExpirationMs))
            .signWith(secretKey)
            .compact()
    }

    fun generateVerificationToken(user: User, action: MailActionEnum): String {
        return Jwts.builder()
            .setSubject(user.email)
            .claim("id", user.id)
            .claim("action", action)
            .setIssuedAt(Date())
            .setExpiration(Date(System.currentTimeMillis() + verificationTokenExpirationMs))
            .signWith(secretKey)
            .compact()
    }

    fun validateToken(token: String): Boolean {
        return try {
            if (tokenBlacklistService.isTokenBlacklisted(token)) {
                logger.info("Token is blacklisted")
                return false
            }
            Jwts.parserBuilder().setSigningKey(secretKey).build().parseClaimsJws(token)
            true
        } catch (e: SignatureException) {
            logger.warn("Invalid token signature: ${e.message}")
            false
        } catch (e: Exception) {
            logger.warn("Token validation error: ${e.message}")
            false
        }
    }

    fun getSubjectFromToken(token: String): String {
        return Jwts.parserBuilder().setSigningKey(secretKey).build().parseClaimsJws(token).body.subject
    }

    fun getIdFromToken(token: String): String {
        return Jwts.parserBuilder().setSigningKey(secretKey).build()
            .parseClaimsJws(token).body.get("id") as String
    }

    fun getActionFromToken(token: String): MailActionEnum {
        val action =
            Jwts.parserBuilder().setSigningKey(secretKey).build().parseClaimsJws(token).body["action"] as String
        return MailActionEnum.valueOf(action)
    }
}