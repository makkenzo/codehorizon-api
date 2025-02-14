package com.makkenzo.codehorizon.config

import io.minio.MinioClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CloudflareR2Config {

    @Bean
    fun minioClient(): MinioClient {
        return MinioClient.builder()
            .endpoint("https://79baaf3545155ae031d4cac51e616665.r2.cloudflarestorage.com") // Cloudflare R2 endpoint
            .credentials(
                "82797a312858c3b01e684c368df70799",
                "eae2d1129a342dadf7a440379e6591e3e6c94db4e3b4e1326d9410ce08a3c85e"
            )
            .build()
    }
}
