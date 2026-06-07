package com.rebenbot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;

/**
 * Configuration for HTTP clients and utilities.
 * Enables automatic cookie and redirect handling via Java's built-in mechanisms.
 * 
 * Automatic behavior:
 * - SimpleClientHttpRequestFactory uses URLConnection which handles redirects by default
 * - CookieManager automatically stores Set-Cookie headers and sends them on subsequent requests
 * - No additional libraries required
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        // Set up global cookie manager for automatic session handling
        // This ensures all URLConnections (used by SimpleClientHttpRequestFactory) 
        // share cookies across requests
        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        CookieHandler.setDefault(cookieManager);
        
        // Use SimpleClientHttpRequestFactory (default, uses URLConnection)
        // URLConnection automatically:
        // 1. Follows 3xx redirects
        // 2. Sends cookies from Set-Cookie headers via the CookieManager
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30000);
        factory.setReadTimeout(30000);
        
        return builder
                .requestFactory(() -> new BufferingClientHttpRequestFactory(factory))
                .build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

}
