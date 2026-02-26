package com.example.agenteditor.api.v1;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Health check endpoint for the backend.
 * <p>
 * GET /api/v1/health returns 200 with status and service name.
 * </p>
 */
@RestController
@RequestMapping("/api/v1")
@Slf4j
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        log.trace("Health check");
        return ResponseEntity.ok(Map.of("status", "UP", "service", "agent-ui-editor-be"));
    }
}
