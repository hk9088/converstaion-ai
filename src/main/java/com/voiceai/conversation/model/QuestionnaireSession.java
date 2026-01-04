package com.voiceai.conversation.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the state of a questionnaire session including current question,
 * collected responses, and retry attempts.
 */
@Data
public class QuestionnaireSession {
    private String sessionId;
    private int currentQuestionIndex;
    private int retryCount;
    private Map<Integer, String> responses; // questionId -> category
    private List<String> transcriptHistory;
    private LocalDateTime startTime;
    private LocalDateTime lastActivityTime;
    private boolean completed;

    public QuestionnaireSession(String sessionId) {
        this.sessionId = sessionId;
        this.currentQuestionIndex = 0;
        this.retryCount = 0;
        this.responses = new HashMap<>();
        this.transcriptHistory = new ArrayList<>();
        this.startTime = LocalDateTime.now();
        this.lastActivityTime = LocalDateTime.now();
        this.completed = false;
    }

    /**
     * Records a successful response and moves to next question.
     */
    public void recordResponse(int questionId, String category, String transcript) {
        responses.put(questionId, category);
        transcriptHistory.add(String.format("Q%d: %s", questionId, transcript));
        currentQuestionIndex++;
        retryCount = 0;
        lastActivityTime = LocalDateTime.now();
    }

    /**
     * Increments retry counter for current question.
     */
    public void incrementRetry() {
        retryCount++;
        lastActivityTime = LocalDateTime.now();
    }

    /**
     * Resets retry counter (used when moving to next question).
     */
    public void resetRetry() {
        retryCount = 0;
    }

    /**
     * Checks if max retries exceeded.
     */
    public boolean hasExceededRetries(int maxRetries) {
        return retryCount >= maxRetries;
    }

    /**
     * Marks session as completed.
     */
    public void complete() {
        this.completed = true;
        this.lastActivityTime = LocalDateTime.now();
    }
}
