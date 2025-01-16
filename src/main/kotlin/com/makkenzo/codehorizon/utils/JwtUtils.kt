package com.makkenzo.codehorizon.utils

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.util.*
import javax.crypto.SecretKey

@Component
class JwtUtils {
    private val secretKey: SecretKey = Keys.secretKeyFor(SignatureAlgorithm.HS256)
    private val accessTokenExpirationMs = 900_000
    private val refreshTokenExpirationMs = 604_800_000

    fun generateAccessToken(email: String): String {
        return Jwts.builder().setSubject(email).setIssuedAt(Date())
            .setExpiration(Date(System.currentTimeMillis() + accessTokenExpirationMs)).signWith(secretKey).compact()
    }

    fun generateRefreshToken(email: String): String {
        return Jwts.builder().setSubject(email).setIssuedAt(Date())
            .setExpiration(Date(System.currentTimeMillis() + refreshTokenExpirationMs)).signWith(secretKey).compact()
    }

    fun validateToken(token: String): Boolean {
        return try {
            Jwts.parserBuilder().setSigningKey(secretKey).build().parseClaimsJws(token)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getEmailFromToken(token: String): String {
        return Jwts.parserBuilder().setSigningKey(secretKey).build().parseClaimsJws(token).body.subject
    }
}