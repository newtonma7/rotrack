package com.rotrack.controller;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.rotrack.config.RateLimitConfiguration;
import com.rotrack.config.SecurityConfig;
import com.rotrack.exception.GlobalExceptionHandler;
import com.rotrack.repository.StudyPresenceRepository;
import com.rotrack.service.StudyPresenceService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StudyPresenceController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, RateLimitConfiguration.class, StudyPresenceService.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://example.test/jwks",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://example.test/issuer"
})
class StudyPresenceControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean StudyPresenceRepository repository;
    @MockitoBean JwtDecoder decoder;

    @Test void returnsOnlyBooleanUsingJwtViewerAndNoStore() throws Exception {
        UUID viewer = UUID.randomUUID();
        for (boolean studying : new boolean[]{true, false}) {
            when(repository.project(viewer, "alice")).thenReturn(List.of(studying));
            mvc.perform(get("/api/v1/social/presence/alice").with(jwt().jwt(token -> token.subject(viewer.toString()))))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.data.studying").value(studying))
                    .andExpect(jsonPath("$.data.*", org.hamcrest.Matchers.hasSize(1)));
        }
        verify(repository, times(2)).project(viewer, "alice");
    }

    @Test void invalidUsernameReturnsSafeNoStoreValidationErrorBeforeDatabaseAccess() throws Exception {
        UUID viewer = UUID.randomUUID();
        mvc.perform(get("/api/v1/social/presence/x").with(jwt().jwt(token -> token.subject(viewer.toString()))))
                .andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fieldErrors.username").isString())
                .andExpect(jsonPath("$.path").value("/api/v1/social/presence/{username}"));
        verifyNoInteractions(repository);
    }

    @Test void securityAndUnavailableErrorsAreNoStoreWithSanitizedPath() throws Exception {
        mvc.perform(get("/api/v1/social/presence/alice")).andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.path").value("/api/v1/social/presence/{username}"));
        UUID viewer = UUID.randomUUID();
        when(repository.project(viewer, "alice")).thenReturn(List.of());
        mvc.perform(get("/api/v1/social/presence/alice").with(jwt().jwt(token -> token.subject(viewer.toString()))))
                .andExpect(status().isNotFound()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/v1/social/presence/{username}"));
    }
}
