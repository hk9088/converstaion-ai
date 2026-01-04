package com.voiceai.conversation.config.exception;

public class TranscriptionException extends RuntimeException {
    public TranscriptionException(String message, Throwable cause) {
        super(message, cause);
    }
}