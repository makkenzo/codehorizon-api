package com.makkenzo.codehorizon.configs

import io.minio.MinioClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CloudflareR2Config {
    private val endpoint = System.getenv("R2_ENDPOINT") ?: throw RuntimeException("Missing R2_ENDPOINT")
    private val accessKey = System.getenv("R2_ACCESS_KEY") ?: throw RuntimeException("Missing R2_ACCESS_KEY")
    private val secretKey = System.getenv("R2_SECRET_KEY") ?: throw RuntimeException("Missing R2_SECRET_KEY")

    @Bean
    fun minioClient(): MinioClient {
        return MinioClient.builder()
            .endpoint(endpoint) // Cloudflare R2 endpoint
            .credentials(
                accessKey,
                secretKey
            )
            .build()
    }
}
