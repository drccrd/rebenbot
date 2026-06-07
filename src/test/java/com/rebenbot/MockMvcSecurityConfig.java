package com.rebenbot;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;

/**
 * Restores Spring Boot 3.x MockMvcSecurityAutoConfiguration behaviour that was
 * removed in Spring Boot 4.  Without this, @WithMockUser has no effect on
 * stateless (NullSecurityContextRepository) MockMvc tests because
 * SecurityContextHolderFilter overwrites the mock SecurityContext before the
 * request reaches the controllers.
 */
@TestConfiguration
public class MockMvcSecurityConfig {

    @Bean
    public MockMvcBuilderCustomizer securityMockMvcCustomizer() {
        return builder -> builder.apply(SecurityMockMvcConfigurers.springSecurity());
    }
}
