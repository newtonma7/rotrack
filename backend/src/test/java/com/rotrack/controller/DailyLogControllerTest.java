package com.rotrack.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rotrack.config.SecurityConfig;
import com.rotrack.dto.DailyLogDTO;
import com.rotrack.dto.ReflectionDTO;
import com.rotrack.exception.GlobalExceptionHandler;
import com.rotrack.exception.ValidationException;
import com.rotrack.service.DailyLogService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DailyLogController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://example.test/jwks",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://example.test/issuer"
})
class DailyLogControllerTest {
    private static final UUID OWNER = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID KEY = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Autowired MockMvc mockMvc;
    @MockitoBean DailyLogService service;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void summaryIsOwnerScopedAndDoesNotExposePrivateDetailFields() throws Exception {
        DailyLogDTO.Summary summary = new DailyLogDTO.Summary(LocalDate.parse("2026-01-01"), 12, 3, 1, 0, false);
        when(service.summaries(OWNER, "2026-01-01", "2026-01-02", "UTC"))
                .thenReturn(new DailyLogDTO.SummaryPage(
                        new DailyLogDTO.Range(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-02"), "UTC"),
                        List.of(summary)));

        mockMvc.perform(get("/api/v1/daily-logs").queryParam("start", "2026-01-01")
                        .queryParam("end", "2026-01-02").queryParam("timeZone", "UTC")
                        .with(jwt().jwt(token -> token.subject(OWNER.toString()).audience(List.of("authenticated")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days[0].workSeconds").value(12))
                .andExpect(jsonPath("$.data.days[0].sessionLabel").doesNotExist())
                .andExpect(jsonPath("$.data.days[0].notePreview").doesNotExist());
        verify(service).summaries(OWNER, "2026-01-01", "2026-01-02", "UTC");
    }

    @Test
    void reflectionRequestRequiresVersionAndRejectsUnknownClientSuppliedFields() throws Exception {
        String prefix = "{\"contentJson\":{\"schemaVersion\":1,\"document\":{\"type\":\"doc\",\"content\":[]}}";
        for (String body : List.of(prefix + "}", prefix + ",\"expectedVersion\":null}",
                prefix + ",\"expectedVersion\":0,\"userId\":\"forged\"}",
                prefix + ",\"expectedVersion\":0,\"duration\":12}",
                prefix + ",\"expectedVersion\":0,\"generated\":{}}")) {
            mockMvc.perform(put("/api/v1/daily-logs/2026-01-01/reflection").queryParam("timeZone", "UTC")
                            .header("Idempotency-Key", KEY.toString()).contentType(MediaType.APPLICATION_JSON).content(body)
                            .with(jwt().jwt(token -> token.subject(OWNER.toString()).audience(List.of("authenticated")))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void reflectionWritePreservesCreationKeyAndRejectsNullBody() throws Exception {
        when(service.updateReflection(eq(OWNER), eq("2026-01-01"), eq("UTC"), eq(KEY), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ReflectionDTO(null, "private", 1, 1, "UTC", null));
        mockMvc.perform(put("/api/v1/daily-logs/2026-01-01/reflection").queryParam("timeZone", "UTC")
                        .header("Idempotency-Key", KEY.toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentJson\":{\"schemaVersion\":1,\"document\":{\"type\":\"doc\",\"content\":[]}},\"expectedVersion\":0}")
                        .with(jwt().jwt(token -> token.subject(OWNER.toString()).audience(List.of("authenticated")))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1));
        mockMvc.perform(put("/api/v1/daily-logs/2026-01-01/reflection").queryParam("timeZone", "UTC")
                        .contentType(MediaType.APPLICATION_JSON).content("")
                        .with(jwt().jwt(token -> token.subject(OWNER.toString()).audience(List.of("authenticated")))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
