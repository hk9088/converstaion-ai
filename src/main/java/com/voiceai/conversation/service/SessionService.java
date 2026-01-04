package com.voiceai.conversation.service;


import com.voiceai.conversation.config.exception.SessionNotFoundException;
import com.voiceai.conversation.model.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Service for managing questionnaire sessions in Redis.
 * Provides session lifecycle management with TTL support.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private static final String SESSION_KEY_PREFIX = "questionnaire:session:";

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${session.timeout-minutes:30}")
    private long sessionTimeoutMinutes;

    public String createSession() {
        String sessionId = UUID.randomUUID().toString();
        Session session = new Session(sessionId);

        String key = buildKey(sessionId);
        redisTemplate.opsForValue().set(
                key,
                session,
                Duration.ofMinutes(sessionTimeoutMinutes)
        );

        log.info("Created session: {} with TTL: {} minutes", sessionId, sessionTimeoutMinutes);
        return sessionId;
    }

    public Session getSession(String sessionId) {
        String key = buildKey(sessionId);
        Session session = (Session) redisTemplate.opsForValue().get(key);

        if (session == null) {
            log.warn("Session not found or expired: {}", sessionId);
            throw new SessionNotFoundException(sessionId);
        }

        return session;
    }

    public void saveSession(Session session) {
        String key = buildKey(session.getSessionId());
        redisTemplate.opsForValue().set(
                key,
                session,
                Duration.ofMinutes(sessionTimeoutMinutes)
        );

        log.debug("Saved session: {}", session.getSessionId());
    }

    public void deleteSession(String sessionId) {
        String key = buildKey(sessionId);
        Boolean deleted = redisTemplate.delete(key);

        if (Boolean.TRUE.equals(deleted)) {
            log.info("Deleted session: {}", sessionId);
        } else {
            log.warn("Failed to delete session (may not exist): {}", sessionId);
        }
    }

    public boolean sessionExists(String sessionId) {
        String key = buildKey(sessionId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public void extendSession(String sessionId) {
        String key = buildKey(sessionId);
        redisTemplate.expire(key, Duration.ofMinutes(sessionTimeoutMinutes));
        log.debug("Extended session TTL: {}", sessionId);
    }

    private String buildKey(String sessionId) {
        return SESSION_KEY_PREFIX + sessionId;
    }
}