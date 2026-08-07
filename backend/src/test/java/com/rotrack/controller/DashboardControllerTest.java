package com.rotrack.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rotrack.config.SecurityConfig;
import com.rotrack.dto.DailyStatsDTO;
import com.rotrack.dto.DashboardRangeDTO;
import com.rotrack.dto.DashboardStatsDTO;
import com.rotrack.exception.GlobalExceptionHandler;
import com.rotrack.model.ActivityType;
import com.rotrack.service.DashboardService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://example.test/jwks",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://example.test/issuer"
})
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void returnsOwnedStatsForTheRequestedLocalDateRange() throws Exception {
        UUID userId = UUID.randomUUID();
        LocalDate startDate = LocalDate.parse("2026-03-08");
        LocalDate endDate = LocalDate.parse("2026-03-09");
        DashboardStatsDTO stats = new DashboardStatsDTO(
                new DashboardRangeDTO(
                        Instant.parse("2026-03-08T05:00:00Z"),
                        Instant.parse("2026-03-09T04:00:00Z"),
                        "America/New_York"
                ),
                Map.of(ActivityType.WORK, 82_800L, ActivityType.ROT, 0L),
                List.of(new DailyStatsDTO(startDate, 82_800L, 0L)),
                List.of(),
                100
        );
        when(dashboardService.getStats(userId, "America/New_York", startDate, endDate))
                .thenReturn(stats);

        mockMvc.perform(get("/api/v1/dashboard/stats")
                        .queryParam("timeZone", "America/New_York")
                        .queryParam("start", "2026-03-08")
                        .queryParam("end", "2026-03-09")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.range.timeZone").value("America/New_York"))
                .andExpect(jsonPath("$.data.totalSeconds.WORK").value(82_800))
                .andExpect(jsonPath("$.data.daily[0].localDate").value("2026-03-08"));

        verify(dashboardService).getStats(userId, "America/New_York", startDate, endDate);
    }

    @Test
    void pairedDateValidationReturnsStableBadRequestEnvelope() throws Exception {
        UUID userId = UUID.randomUUID();
        when(dashboardService.getStats(userId, "UTC", LocalDate.parse("2026-03-08"), null))
                .thenThrow(new IllegalArgumentException("start and end must be provided together"));

        mockMvc.perform(get("/api/v1/dashboard/stats")
                        .queryParam("timeZone", "UTC")
                        .queryParam("start", "2026-03-08")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.path").value("/api/v1/dashboard/stats"));
    }

    @Test
    void invalidTimeZoneReturnsStableBadRequestEnvelope() throws Exception {
        UUID userId = UUID.randomUUID();
        when(dashboardService.getStats(userId, "Not/A_Zone", null, null))
                .thenThrow(new IllegalArgumentException("timeZone must be a valid IANA identifier"));

        mockMvc.perform(get("/api/v1/dashboard/stats")
                        .queryParam("timeZone", "Not/A_Zone")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.path").value("/api/v1/dashboard/stats"));
    }

    @Test
    void invalidLocalDateReturnsStableBadRequestEnvelope() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/dashboard/stats")
                        .queryParam("timeZone", "America/New_York")
                        .queryParam("start", "not-a-date")
                        .queryParam("end", "2026-03-09")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.path").value("/api/v1/dashboard/stats"));
    }

    @Test
    void missingTimeZoneReturnsStableBadRequestEnvelope() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/dashboard/stats")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.path").value("/api/v1/dashboard/stats"));
    }
}
