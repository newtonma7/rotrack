package com.rotrack.controller;

import com.rotrack.dto.ApiResponse;
import com.rotrack.dto.StudyPresenceDTO;
import com.rotrack.service.StudyPresenceService;
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
@RequestMapping("/api/v1/social/presence")
public class StudyPresenceController {
    private final StudyPresenceService service;

    public StudyPresenceController(StudyPresenceService service) { this.service = service; }

    @GetMapping("/{username}")
    public ResponseEntity<ApiResponse<StudyPresenceDTO>> get(@AuthenticationPrincipal Jwt jwt, @PathVariable String username) {
        // Only the validated subject selects the viewer; the URL identifies the peer, never authority.
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(service.get(UUID.fromString(jwt.getSubject()), username)));
    }
}
