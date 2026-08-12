package com.rotrack.controller;

import com.rotrack.dto.ApiResponse;
import com.rotrack.dto.PreferencesDTO;
import com.rotrack.dto.UpdatePreferencesRequest;
import com.rotrack.service.PreferencesService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/preferences")
public class PreferencesController {

    private final PreferencesService preferencesService;

    public PreferencesController(PreferencesService preferencesService) {
        this.preferencesService = preferencesService;
    }

    @GetMapping
    public ApiResponse<PreferencesDTO> getPreferences(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(preferencesService.getPreferences(userId(jwt)));
    }

    @PutMapping
    public ApiResponse<PreferencesDTO> updatePreferences(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdatePreferencesRequest request
    ) {
        return ApiResponse.success(preferencesService.updatePreferences(
                userId(jwt),
                request.timeZone(),
                request.dailyWorkGoalMinutes(),
                request.shareStudySummary(),
                request.shareActiveStudyStatus()
        ));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
