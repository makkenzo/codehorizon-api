package com.makkenzo.codehorizon.configs

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy
import io.github.bucket4j.distributed.proxy.ProxyManager
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.codec.ByteArrayCodec
import io.lettuce.core.codec.StringCodec
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import java.time.Duration

@Configuration
class RateLimitingConfig(private val lettuceConnectionFactory: LettuceConnectionFactory) {
    @Value("\${spring.data.redis.host}")
    private lateinit var redisHost: String

    @Value("\${spring.data.redis.port}")
    private lateinit var redisPort: String

    @Bean
    fun rateLimiterRedisConnection(): StatefulRedisConnection<String, ByteArray> {
        val redisUri = StringBuilder("redis://")
        redisUri.append("$redisHost:$redisPort")

        val redisClient = RedisClient.create(redisUri.toString())
        return redisClient.connect(io.lettuce.core.codec.RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE))
    }

    @Bean
    fun rateLimiterProxyManager(redisConnection: StatefulRedisConnection<String, ByteArray>): ProxyManager<String> {
        val ttl = Duration.ofHours(2)

        return LettuceBasedProxyManager.builderFor(redisConnection)
            .withExpirationStrategy(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(ttl))
            .build()
    }
}

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RateLimited(
    val limit: Long = 100,
    val durationSeconds: Long = 60,
    val keyPrefix: String = "rl_",
    val strategy: KeyStrategy = KeyStrategy.IP_ADDRESS
)

enum class KeyStrategy {
    IP_ADDRESS,
    USER_ID
}