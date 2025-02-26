package com.makkenzo.codehorizon.configs

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "cookie")
class CookieConfig {
    var secure: Boolean = false
}