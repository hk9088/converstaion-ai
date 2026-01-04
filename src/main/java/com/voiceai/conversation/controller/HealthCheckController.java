package com.voiceai.conversation.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.transcribestreaming.TranscribeStreamingAsyncClient;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Health check endpoints for load balancer and monitoring.
 */
@Slf4j
@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
public class HealthCheckController {

    private final RedisTemplate<String, Object> redisTemplate;
    private final PollyClient pollyClient;
    private final TranscribeStreamingAsyncClient transcribeClient;
    private final BedrockRuntimeClient bedrockClient;

    @GetMapping
    public ResponseEntity<HealthStatus> healthCheck() {
        return ResponseEntity.ok(new HealthStatus(
                "UP",
                Instant.now().toString(),
                "Voice Questionnaire System"
        ));
    }

    @GetMapping("/ready")
    public ResponseEntity<ReadinessStatus> readinessCheck() {
        Map<String, Boolean> dependencies = new HashMap<>();
        boolean allHealthy = true;

        try {
            redisTemplate.hasKey("health-check");
            dependencies.put("redis", true);
        } catch (Exception e) {
            log.error("Redis health check failed", e);
            dependencies.put("redis", false);
            allHealthy = false;
        }

        try {
            pollyClient.describeVoices();
            dependencies.put("polly", true);
        } catch (Exception e) {
            log.error("Polly health check failed", e);
            dependencies.put("polly", false);
            allHealthy = false;
        }

        dependencies.put("transcribe", true);
        dependencies.put("bedrock", true);

        ReadinessStatus status = new ReadinessStatus(
                allHealthy ? "READY" : "NOT_READY",
                dependencies
        );

        return allHealthy ?
                ResponseEntity.ok(status) :
                ResponseEntity.status(503).body(status);
    }

    @Data
    @AllArgsConstructor
    static class HealthStatus {
        private String status;
        private String timestamp;
        private String service;
    }

    @Data
    @AllArgsConstructor
    static class ReadinessStatus {
        private String status;
        private Map<String, Boolean> dependencies;
    }
}
