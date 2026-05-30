package com.rebenbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Centralised CORS configuration.
 * In development the Vite dev server runs on a different port — allow it via
 * the {@code cors.allowed-origin} property.  In production the frontend is
 * served from the same origin so no CORS header is needed; set the property
 * to an empty string to disable CORS entirely.
 *
 * <p>Exposes both a {@link WebMvcConfigurer} (MVC / DispatcherServlet level) and a
 * {@link CorsConfigurationSource} bean (Spring Security filter-chain level) so that
 * OPTIONS preflight requests are resolved before the authorisation check.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origin:}")
    private String allowedOrigin;

    /** Used by Spring Security's CorsFilter — must run before authorisation. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (allowedOrigin != null && !allowedOrigin.isBlank()) {
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOrigins(List.of(allowedOrigin));
            config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
            config.setAllowedHeaders(List.of("*"));
            config.setMaxAge(3600L);
            source.registerCorsConfiguration("/**", config);
        }
        return source;
    }

    /** Used by Spring MVC at the DispatcherServlet level. */
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
