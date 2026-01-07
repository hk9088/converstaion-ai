package com.voiceai.conversation.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a questionnaire session with complete state tracking.
 * Serializable for Redis storage.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Session implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String sessionId;
    private int currentQuestionIndex;
    private int retryCount;
    private Map<Integer, UserResponse> responses;
    private List<String> transcriptHistory;
    private Instant createdAt;
    private Instant lastModifiedAt;
    private SessionStatus status;
    private boolean hasMaxRetriesExceeded;

    public Session(String sessionId) {
        this.sessionId = sessionId;
        this.currentQuestionIndex = 0;
        this.retryCount = 0;
        this.responses = new HashMap<>();
        this.transcriptHistory = new ArrayList<>();
        this.createdAt = Instant.now();
        this.lastModifiedAt = Instant.now();
        this.status = SessionStatus.ACTIVE;
        this.hasMaxRetriesExceeded = false;
    }

    public void recordResponse(UserResponse response) {
        this.responses.put(response.getQuestionId(), response);
        this.transcriptHistory.add(String.format("Q%d: %s -> %s",
                response.getQuestionId(),
                response.getTranscript(),
                response.getClassifiedCategory()));
        this.currentQuestionIndex++;
        this.retryCount = 0;
        this.lastModifiedAt = Instant.now();
    }

    public void incrementRetry() {
        this.retryCount++;
        this.lastModifiedAt = Instant.now();
    }

    public void markMaxRetriesExceeded() {
        this.hasMaxRetriesExceeded = true;
        this.lastModifiedAt = Instant.now();
    }

    public void resetRetry() {
        this.retryCount = 0;
        this.lastModifiedAt = Instant.now();
    }

    public void complete() {
        this.status = SessionStatus.COMPLETED;
        this.lastModifiedAt = Instant.now();
    }

    public void expire() {
        this.status = SessionStatus.EXPIRED;
        this.lastModifiedAt = Instant.now();
    }

    public boolean isActive() {
        return SessionStatus.ACTIVE.equals(this.status);
    }

    public boolean isCompleted() {
        return SessionStatus.COMPLETED.equals(this.status);
    }

    public boolean isSuccessful() {
        return isCompleted();
    }

    public enum SessionStatus {
        ACTIVE,
        COMPLETED,
        EXPIRED
    }
}