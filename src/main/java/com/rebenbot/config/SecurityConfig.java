package com.rebenbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for Rebenbot.
 *
 * <p>Uses HTTP Basic authentication over a single API user whose credentials are
 * supplied via {@code security.api.username} and {@code security.api.password}
 * application properties (both support env-var injection for production deployments).
 *
 * <p>Design decisions:
 * <ul>
 *   <li>CSRF is disabled — the API is consumed by a SPA and no browser-managed
 *       session cookie is ever issued (stateless).</li>
 *   <li>Sessions are STATELESS — each request must carry credentials.</li>
 *   <li>CORS is delegated to the existing {@link CorsConfig} WebMvcConfigurer
 *       via {@code Customizer.withDefaults()}.</li>
 *   <li>{@code GET /v1/health} is left open so load-balancer/probe traffic does
 *       not need credentials.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${security.api.username:${REBENBOT_API_USER}}")
    private String apiUsername;

    @Value("${security.api.password:${REBENBOT_API_PASSWORD}}")
    private String apiPassword;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        String encoded = encoder.encode(apiPassword);
        return new InMemoryUserDetailsManager(
                User.withUsername(apiUsername)
                        .password(encoded)
                        .roles("USER")
                        .build());
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Delegate pre-flight / CORS to the existing CorsConfig WebMvcConfigurer
                .cors(Customizer.withDefaults())
                // REST API — no browser session, no CSRF token needed
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Health probe is unauthenticated so uptime monitors work without credentials
                        .requestMatchers("/v1/health").permitAll()
                        // Everything else requires a valid API user
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
