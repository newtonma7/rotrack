package com.rotrack.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rotrack.config.SecurityConfig;
import com.rotrack.dto.PreferencesDTO;
import com.rotrack.dto.UpdatePreferencesRequest;
import com.rotrack.exception.GlobalExceptionHandler;
import com.rotrack.service.PreferencesService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PreferencesController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://example.test/jwks",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://example.test/issuer"
})
class PreferencesControllerTest {

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PreferencesService preferencesService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void serializesPreferencesUsingTheSharedTimeZoneJsonName() throws Exception {
        String json = objectMapper.writeValueAsString(new PreferencesDTO("America/New_York", 60, false, true));

        org.assertj.core.api.Assertions.assertThat(json)
                .contains("\"timeZone\":\"America/New_York\"")
                .doesNotContain("\"timezone\"");

        UpdatePreferencesRequest request = objectMapper.readValue(
                "{\"timeZone\":\"Europe/Berlin\",\"dailyWorkGoalMinutes\":60,"
                        + "\"shareStudySummary\":false,\"shareActiveStudyStatus\":true}",
                UpdatePreferencesRequest.class
        );
        org.assertj.core.api.Assertions.assertThat(request.timeZone()).isEqualTo("Europe/Berlin");
    }

    @Test
    void getsPreferencesForTheJwtSubject() throws Exception {
        when(preferencesService.getPreferences(USER_ID))
                .thenReturn(new PreferencesDTO("UTC", 60, false, true));

        mockMvc.perform(get("/api/v1/preferences")
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timeZone").value("UTC"))
                .andExpect(jsonPath("$.data.dailyWorkGoalMinutes").value(60))
                .andExpect(jsonPath("$.data.shareStudySummary").value(false))
                .andExpect(jsonPath("$.data.shareActiveStudyStatus").value(true));

        verify(preferencesService).getPreferences(USER_ID);
    }

    @Test
    void putsPreferencesWithoutAcceptingARequestUserId() throws Exception {
        UpdatePreferencesRequest request = new UpdatePreferencesRequest("America/New_York", 120, true, false);
        when(preferencesService.updatePreferences(
                eq(USER_ID), eq("America/New_York"), eq(120), eq(true), eq(false)))
                .thenReturn(new PreferencesDTO("America/New_York", 120, true, false));

        mockMvc.perform(put("/api/v1/preferences")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(request))
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timeZone").value("America/New_York"))
                .andExpect(jsonPath("$.data.dailyWorkGoalMinutes").value(120));

        verify(preferencesService).updatePreferences(USER_ID, "America/New_York", 120, true, false);
    }

    @Test
    void associatesInvalidTimeZoneWithTheTimeZoneField() throws Exception {
        mockMvc.perform(put("/api/v1/preferences")
                        .contentType("application/json")
                        .content("{\"timeZone\":\"Not/A_Zone\",\"dailyWorkGoalMinutes\":60,"
                                + "\"shareStudySummary\":false,\"shareActiveStudyStatus\":false}")
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fieldErrors.timeZone").value("must be a valid IANA identifier"));
    }

    @Test
    void rejectsUnauthenticatedAccess() throws Exception {
        mockMvc.perform(get("/api/v1/preferences"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void rejectsInvalidRequestWithFieldErrors() throws Exception {
        mockMvc.perform(put("/api/v1/preferences")
                        .contentType("application/json")
                        .content("{\"timeZone\":\"UTC\",\"dailyWorkGoalMinutes\":1441,\"shareStudySummary\":false,\"shareActiveStudyStatus\":false}")
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fieldErrors.dailyWorkGoalMinutes").exists());
    }
}
