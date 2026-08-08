package com.rotrack.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rotrack.config.SecurityConfig;
import com.rotrack.health.DatabaseReadinessProbe;
import com.rotrack.service.TimeEntryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({TimeEntryController.class, ReadinessController.class})
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://example.test/jwks",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://example.test/issuer",
        "rotrack.cors.allowed-origins=http://localhost:3000"
})
class HealthEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DatabaseReadinessProbe databaseReadinessProbe;

    @MockitoBean
    private TimeEntryService timeEntryService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void livenessIsPublicAndDoesNotDependOnDatabaseReadiness() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        verifyNoInteractions(databaseReadinessProbe);
    }

    @Test
    void readinessIsPublicAndReportsReadyWithoutDetails() throws Exception {
        when(databaseReadinessProbe.isReady()).thenReturn(true);

        mockMvc.perform(get("/api/v1/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ready"))
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void readinessReturnsServiceUnavailableWithoutLeakingDependencyDetails() throws Exception {
        when(databaseReadinessProbe.isReady()).thenReturn(false);

        mockMvc.perform(get("/api/v1/readiness"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("not_ready"))
                .andExpect(jsonPath("$.details").doesNotExist());
    }
}
