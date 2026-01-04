package com.voiceai.conversation.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionStartResponse {
    private String sessionId;
    private String message;
    private long expiresInSeconds;
}
