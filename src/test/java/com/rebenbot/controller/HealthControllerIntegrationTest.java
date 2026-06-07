package com.rebenbot.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import com.rebenbot.MockMvcSecurityConfig;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for HealthController and Spring Security configuration.
 *
 * Starts the full Spring application context (H2 in-memory, Flyway migrations run).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MockMvcSecurityConfig.class)
@org.springframework.test.context.ActiveProfiles("test")
class HealthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // -----------------------------------------------------------------------
    // /v1/health — permitAll
    // -----------------------------------------------------------------------

    @Test
    void healthEndpoint_noCredentials_returns200() throws Exception {
        mockMvc.perform(get("/v1/health").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("Rebenbot"))
                .andExpect(jsonPath("$.version").value("0.1.0"));
    }

    // -----------------------------------------------------------------------
    // Protected endpoints — require authentication
    // -----------------------------------------------------------------------

    @Test
    void protectedEndpoint_noCredentials_returns401() throws Exception {
        mockMvc.perform(get("/v1/vineyard").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void protectedEndpoint_withCredentials_doesNotReturn401() throws Exception {
        // Any 2xx or 4xx other than 401/403 is acceptable — we just verify
        // the security layer lets the request through.
        mockMvc.perform(get("/v1/vineyard").accept(MediaType.APPLICATION_JSON))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 401 || status == 403) {
                        throw new AssertionError("Expected authenticated request to be allowed, got " + status);
                    }
                });
    }
}
