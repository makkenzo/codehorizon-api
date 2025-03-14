package com.makkenzo.codehorizon

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching

@SpringBootApplication
@EnableCaching
class CodehorizonApplication


fun main(args: Array<String>) {
    runApplication<CodehorizonApplication>(*args)
}
