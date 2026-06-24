package com.logfriends.platform.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    @Value("\${logfriends.web.allowed-origins:http://localhost:3000}")
    private val allowedOrigins: String,
) : WebMvcConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/api/**")
            .allowedOrigins(*allowedOriginsList())
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")

        registry.addMapping("/ingest")
            .allowedOrigins(*allowedOriginsList())
            .allowedMethods("POST", "OPTIONS")
            .allowedHeaders("*")
    }

    private fun allowedOriginsList(): Array<String> =
        allowedOrigins
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toTypedArray()
}
