package com.voiceai.conversation.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of classifying a user response via LLM.
 * Matches the JSON format returned by Claude.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClassificationResult {

    @JsonProperty("matched")
    private boolean matched;

    @JsonProperty("category")
    private String category;

    @JsonProperty("confidence")
    private double confidence;

    public boolean isValid(double minConfidence) {
        return matched
                && category != null
                && !category.trim().isEmpty()
                && confidence >= minConfidence;
    }
}