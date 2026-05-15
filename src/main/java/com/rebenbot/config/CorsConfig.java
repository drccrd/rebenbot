package com.rebenbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Centralised CORS configuration.
 * In development the Vite dev server runs on a different port — allow it via
 * the {@code cors.allowed-origin} property.  In production the frontend is
 * served from the same origin so no CORS header is needed; set the property
 * to an empty string to disable CORS entirely.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origin:}")
    private String allowedOrigin;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOrigin == null || allowedOrigin.isBlank()) {
            return;
        }
        registry.addMapping("/v1/**")
                .allowedOrigins(allowedOrigin)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
