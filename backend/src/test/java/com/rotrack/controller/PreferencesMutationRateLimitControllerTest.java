package com.rotrack.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rotrack.config.RateLimitConfiguration;
import com.rotrack.config.SecurityConfig;
import com.rotrack.dto.PreferencesDTO;
import com.rotrack.exception.GlobalExceptionHandler;
import com.rotrack.service.PreferencesService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PreferencesController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, RateLimitConfiguration.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://example.test/jwks",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://example.test/issuer",
        "rotrack.security.rate-limit.requests-per-window=1",
        "rotrack.security.rate-limit.window=1m",
        "rotrack.security.rate-limit.max-keys=10"
})
class PreferencesMutationRateLimitControllerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PreferencesService preferencesService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void limitsAuthenticatedPreferenceUpdatesByTheSameMutationBudget() throws Exception {
        when(preferencesService.updatePreferences(USER_ID, "UTC", 60, false, false))
                .thenReturn(new PreferencesDTO("UTC", 60, false, false));
        String body = "{\"timeZone\":\"UTC\",\"dailyWorkGoalMinutes\":60,"
                + "\"shareStudySummary\":false,\"shareActiveStudyStatus\":false}";

        mockMvc.perform(put("/api/v1/preferences")
                        .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()).audience(java.util.List.of("authenticated"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/preferences")
                        .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()).audience(java.util.List.of("authenticated"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }
}
