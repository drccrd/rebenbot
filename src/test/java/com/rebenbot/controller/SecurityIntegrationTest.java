package com.rebenbot.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies Spring Security configuration across all controllers.
 *
 * <ul>
 *   <li>Every protected endpoint must return 401 when no credentials are supplied.</li>
 *   <li>Every protected endpoint must NOT return 401/403 when a valid user is present.</li>
 *   <li>CORS preflight (OPTIONS) must be resolved before the auth check.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // -----------------------------------------------------------------------
    // Unauthenticated — all protected endpoints must return 401
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "401 without credentials: {0}")
    @ValueSource(strings = {
            "/v1/vineyards",
            "/v1/diseases",
            "/v1/fungicides",
            "/v1/fungicide-management/latest-recommendations",
            "/v1/growth-stage/current",
            "/v1/wbi/prognosis/latest?disease=peronospora",
            "/v1/wbi/incubation/active",
            "/v1/wbi/pheno/latest",
            "/v1/admin/sync/status",
    })
    @DisplayName("Protected endpoint returns 401 without credentials")
    void protectedEndpoints_noCredentials_return401(String path) throws Exception {
        mockMvc.perform(get(path).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    // -----------------------------------------------------------------------
    // Authenticated — all protected endpoints must not return 401/403
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "Authenticated access allowed: {0}")
    @ValueSource(strings = {
            "/v1/vineyards",
            "/v1/diseases",
            "/v1/fungicides",
            "/v1/fungicide-management/latest-recommendations",
            "/v1/growth-stage/current",
            "/v1/wbi/prognosis/latest?disease=peronospora",
            "/v1/wbi/incubation/active",
            "/v1/wbi/pheno/latest",
            "/v1/admin/sync/status",
    })
    @DisplayName("Protected endpoint allows authenticated user")
    void protectedEndpoints_withCredentials_notRejected(String path) throws Exception {
        mockMvc.perform(get(path).with(user("user").roles("USER")).accept(MediaType.APPLICATION_JSON))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 401 || status == 403) {
                        throw new AssertionError(
                                "Expected authenticated request to be allowed on " + path + ", got " + status);
                    }
                });
    }

    // -----------------------------------------------------------------------
    // /v1/health — permitAll (no credentials required)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("/v1/health is accessible without credentials")
    void healthEndpoint_isPermitAll() throws Exception {
        mockMvc.perform(get("/v1/health").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    // -----------------------------------------------------------------------
    // CORS preflight — OPTIONS must succeed before auth is checked
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("OPTIONS preflight is resolved before auth check")
    void corsPreflightRequest_isHandledBeforeAuth() throws Exception {
        mockMvc.perform(options("/v1/vineyards")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                // Must NOT be rejected by security (401/403)
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 401 || status == 403) {
                        throw new AssertionError("CORS preflight blocked by security layer, got " + status);
                    }
                })
                // Must carry the CORS allow-origin header
                .andExpect(header().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
