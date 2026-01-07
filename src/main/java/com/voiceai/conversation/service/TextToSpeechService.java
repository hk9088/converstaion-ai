package com.voiceai.conversation.service;


import com.voiceai.conversation.config.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.polly.model.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Service for text-to-speech synthesis using Amazon Polly.
 * Includes retry logic and metrics tracking.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TextToSpeechService {

    private final PollyClient pollyClient;
    private final MetricsService metricsService;

    @Value("${aws.polly.voice-id:Joanna}")
    private String voiceId;

    @Value("${aws.polly.engine}")
    private String engine;

    public byte[] synthesizeSpeech(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Text cannot be empty");
        }

        log.info("Synthesizing speech for text (length: {})", text.length());
        long startTime = System.currentTimeMillis();

        try {
            SynthesizeSpeechRequest request = SynthesizeSpeechRequest.builder()
                    .text(text)
                    .voiceId(VoiceId.fromValue(voiceId))
                    .outputFormat(OutputFormat.MP3)
                    .engine(engine)
                    .build();

            ResponseInputStream<SynthesizeSpeechResponse> response =
                    pollyClient.synthesizeSpeech(request);

            byte[] audioData = readInputStream(response);

            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordTtsLatency(duration);
            metricsService.incrementTtsSuccess();

            log.info("Speech synthesis successful: {} bytes in {}ms", audioData.length, duration);
            return audioData;

        } catch (PollyException e) {
            metricsService.incrementTtsError();
            log.error("Polly synthesis failed: {}", e.getMessage(), e);
            throw new ServiceUnavailableException("Text-to-Speech", e);
        } catch (IOException e) {
            metricsService.incrementTtsError();
            log.error("Failed to read audio stream: {}", e.getMessage(), e);
            throw new ServiceUnavailableException("Text-to-Speech", e);
        }
    }

    private byte[] readInputStream(ResponseInputStream<?> inputStream) throws IOException {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] data = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(data)) != -1) {
                buffer.write(data, 0, bytesRead);
            }
            return buffer.toByteArray();
        }
    }
}