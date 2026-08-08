package com.rotrack.controller;

import com.rotrack.health.DatabaseReadinessProbe;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ReadinessController {

    private final DatabaseReadinessProbe databaseReadinessProbe;

    public ReadinessController(DatabaseReadinessProbe databaseReadinessProbe) {
        this.databaseReadinessProbe = databaseReadinessProbe;
    }

    /** Readiness reports only a stable state; dependency and credential details stay server-side. */
    @GetMapping("/readiness")
    public ResponseEntity<Map<String, String>> readiness() {
        if (databaseReadinessProbe.isReady()) {
            return ResponseEntity.ok(Map.of("status", "ready"));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", "not_ready"));
    }
}
