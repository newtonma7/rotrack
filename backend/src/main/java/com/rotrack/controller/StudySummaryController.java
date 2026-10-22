package com.rotrack.controller;

import com.rotrack.dto.ApiResponse;
import com.rotrack.dto.StudySummaryDTO;
import com.rotrack.service.StudySummaryService;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/social/summaries")
public class StudySummaryController {
    private final StudySummaryService service;
    public StudySummaryController(StudySummaryService service) { this.service = service; }

    @GetMapping("/{username}")
    public ResponseEntity<ApiResponse<StudySummaryDTO>> get(@AuthenticationPrincipal Jwt jwt, @PathVariable String username) {
        UUID viewer = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.get(viewer, username)));
    }
}
