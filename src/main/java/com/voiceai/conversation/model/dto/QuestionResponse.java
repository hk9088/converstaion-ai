package com.voiceai.conversation.model.dto;

import com.voiceai.conversation.model.Question;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionResponse {
    private Question question;
    private String message;
    private boolean completed;
    private int totalQuestions;
    private int currentQuestionNumber;
}