package com.rotrack.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rotrack.config.SecurityConfig;
import com.rotrack.dto.PreferencesDTO;
import com.rotrack.exception.GlobalExceptionHandler;
import com.rotrack.model.UserPreferences;
import com.rotrack.repository.UserPreferencesRepository;
import com.rotrack.service.PreferencesService;
import java.util.List;
import java.util.Optional;
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
@Import({SecurityConfig.class, GlobalExceptionHandler.class, PreferencesService.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://example.test/jwks",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://example.test/issuer"
})
class PreferencesControllerOwnershipTest {

    private static final UUID USER_A = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID USER_B = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserPreferencesRepository repository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void eachUserReadsOnlyTheRowSelectedByTheirJwtSubject() throws Exception {
        UserPreferences preferences = new UserPreferences();
        preferences.setUserId(USER_A);
        preferences.setTimezone("UTC");
        when(repository.findById(USER_A)).thenReturn(Optional.of(preferences));
        when(repository.findById(USER_B)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/preferences").with(jwt().jwt(token -> token
                        .subject(USER_A.toString()).audience(List.of("authenticated")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timezone").value("UTC"));
        mockMvc.perform(get("/api/v1/preferences").with(jwt().jwt(token -> token
                        .subject(USER_B.toString()).audience(List.of("authenticated")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timezone").doesNotExist())
                .andExpect(jsonPath("$.data.shareStudySummary").value(false));

        verify(repository).findById(USER_A);
        verify(repository).findById(USER_B);
    }
}
