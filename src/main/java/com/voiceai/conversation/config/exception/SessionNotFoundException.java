package com.voiceai.conversation.config.exception;

public class SessionNotFoundException extends RuntimeException {
    public SessionNotFoundException(String sessionId) {
        super("Session not found: " + sessionId);
    }
}