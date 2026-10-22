package com.rotrack.controller;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.rotrack.config.RateLimitConfiguration;
import com.rotrack.config.SecurityConfig;
import com.rotrack.dto.StudySummaryDTO;
import com.rotrack.exception.GlobalExceptionHandler;
import com.rotrack.exception.ResourceNotFoundException;
import com.rotrack.service.StudySummaryService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StudySummaryController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, RateLimitConfiguration.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://example.test/jwks",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://example.test/issuer"
})
class StudySummaryControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean StudySummaryService service;
    @MockitoBean JwtDecoder decoder;

    @Test
    void authenticatedResponseHasOnlyThreeAllowlistedFieldsAndIsNoStore() throws Exception {
        UUID viewer = UUID.randomUUID();
        when(service.get(viewer, "alice")).thenReturn(new StudySummaryDTO(3600, 7, false));
        mvc.perform(get("/api/v1/social/summaries/alice").with(jwt().jwt(token -> token.subject(viewer.toString()))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json("{\"data\":{\"workSeconds\":3600,\"studyDayStreak\":7,\"streakCapped\":false}}"))
                .andExpect(jsonPath("$.data.*", org.hamcrest.Matchers.hasSize(3)));
        verify(service).get(viewer, "alice");
    }

    @Test
    void routeRequiresAuthenticationAndNotFoundIsNoStore() throws Exception {
        mvc.perform(get("/api/v1/social/summaries/alice")).andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"));
        UUID viewer = UUID.randomUUID();
        when(service.get(viewer, "alice")).thenThrow(new ResourceNotFoundException("Study summary not found"));
        mvc.perform(get("/api/v1/social/summaries/alice").with(jwt().jwt(token -> token.subject(viewer.toString()))))
                .andExpect(status().isNotFound()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }
}
