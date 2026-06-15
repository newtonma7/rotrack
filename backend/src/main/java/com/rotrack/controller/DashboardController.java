package com.rotrack.controller;

import com.rotrack.dto.ApiResponse;
import com.rotrack.dto.DashboardStatsDTO;
import com.rotrack.service.TimeEntryService;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final TimeEntryService timeEntryService;

    public DashboardController(TimeEntryService timeEntryService) {
        this.timeEntryService = timeEntryService;
    }

    @GetMapping("/stats")
    public ApiResponse<DashboardStatsDTO> getStats(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ApiResponse.success(timeEntryService.getWeeklyStats(userId));
    }
}
