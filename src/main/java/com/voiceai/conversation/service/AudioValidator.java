package com.voiceai.conversation.service;


import com.voiceai.conversation.config.exception.InvalidAudioException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for validating audio input.
 */
@Slf4j
@Service
public class AudioValidator {

    @Value("${audio.max-size-bytes:10485760}")
    private long maxAudioSizeBytes;

    @Value("${audio.min-size-bytes:1000}")
    private long minAudioSizeBytes;

    private static final byte[] WAV_HEADER = new byte[]{'R', 'I', 'F', 'F'};
    private static final byte[] MP3_HEADER_1 = new byte[]{(byte) 0xFF, (byte) 0xFB};
    private static final byte[] MP3_HEADER_2 = new byte[]{(byte) 0xFF, (byte) 0xF3};
    private static final byte[] MP3_HEADER_3 = new byte[]{(byte) 0xFF, (byte) 0xF2};

    public void validateAudio(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            throw new InvalidAudioException("Audio data is empty");
        }

        if (audioData.length < minAudioSizeBytes) {
            throw new InvalidAudioException(
                    String.format("Audio too short: %d bytes (minimum: %d bytes)",
                            audioData.length, minAudioSizeBytes)
            );
        }

        if (audioData.length > maxAudioSizeBytes) {
            throw new InvalidAudioException(
                    String.format("Audio too large: %d bytes (maximum: %d bytes)",
                            audioData.length, maxAudioSizeBytes)
            );
        }

        if (!isValidAudioFormat(audioData)) {
            throw new InvalidAudioException("Unsupported audio format. Please use WAV or MP3.");
        }

        log.debug("Audio validation passed: {} bytes", audioData.length);
    }

    private boolean isValidAudioFormat(byte[] data) {
        if (data.length < 4) {
            return false;
        }

        return hasWavHeader(data) || hasMp3Header(data);
    }

    private boolean hasWavHeader(byte[] data) {
        if (data.length < WAV_HEADER.length) {
            return false;
        }

        for (int i = 0; i < WAV_HEADER.length; i++) {
            if (data[i] != WAV_HEADER[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean hasMp3Header(byte[] data) {
        if (data.length < 2) {
            return false;
        }

        return matchesHeader(data, MP3_HEADER_1) ||
                matchesHeader(data, MP3_HEADER_2) ||
                matchesHeader(data, MP3_HEADER_3);
    }

    private boolean matchesHeader(byte[] data, byte[] header) {
        for (int i = 0; i < header.length; i++) {
            if (data[i] != header[i]) {
                return false;
            }
        }
        return true;
    }
}
