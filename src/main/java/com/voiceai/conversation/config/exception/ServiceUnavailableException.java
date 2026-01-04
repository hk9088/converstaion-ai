package com.voiceai.conversation.config.exception;

public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String service, Throwable cause) {
        super("Service unavailable: " + service, cause);
    }
}

