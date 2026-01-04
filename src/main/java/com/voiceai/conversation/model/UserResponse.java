package com.voiceai.conversation.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * Represents a user's response to a question.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private int questionId;
    private String transcript;
    private String classifiedCategory;
    private double confidence;
    private Instant recordedAt;

    public UserResponse(int questionId, String transcript, String classifiedCategory, double confidence) {
        this.questionId = questionId;
        this.transcript = transcript;
        this.classifiedCategory = classifiedCategory;
        this.confidence = confidence;
        this.recordedAt = Instant.now();
    }
}