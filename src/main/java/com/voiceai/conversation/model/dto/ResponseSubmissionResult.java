package com.voiceai.conversation.model.dto;

import com.voiceai.conversation.model.Question;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResponseSubmissionResult {
    private String status;
    private String message;
    private String transcript;
    private Question nextQuestion;
    private boolean completed;
    private int retriesRemaining;
    private double confidence;
}
