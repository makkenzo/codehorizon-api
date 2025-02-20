package com.makkenzo.codehorizon.services

import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class TokenBlacklistService {
    private val blacklistedTokens = ConcurrentHashMap<String, Boolean>()

    fun blacklistToken(token: String) {
        blacklistedTokens[token] = true
    }

    fun isTokenBlacklisted(token: String): Boolean {
        return blacklistedTokens.containsKey(token)
    }
}