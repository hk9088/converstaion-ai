package com.voiceai.conversation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.services.transcribestreaming.TranscribeStreamingAsyncClient;
import software.amazon.awssdk.services.transcribestreaming.model.*;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Service for converting speech to text using Amazon Transcribe Streaming.
 * Handles real-time audio transcription with streaming API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranscribeService {

    private final TranscribeStreamingAsyncClient transcribeClient;

    /**
     * Transcribes audio data to text.
     *
     * @param audioData Raw audio data (PCM 16-bit, little-endian, mono, 16 kHz)
     * @return Transcribed text from the audio
     * @throws RuntimeException if transcription fails
     */
    public String transcribeAudio(byte[] audioData) {
        log.info("Starting transcription for {} bytes of audio", audioData.length);

        StringBuilder transcriptBuilder = new StringBuilder();
        CompletableFuture<Void> resultFuture = new CompletableFuture<>();

        try {
            // Request setup - adjust language/encoding/sample rate as needed
            StartStreamTranscriptionRequest request = StartStreamTranscriptionRequest.builder()
                    .languageCode(LanguageCode.EN_US)
                    .mediaEncoding(MediaEncoding.PCM)
                    .mediaSampleRateHertz(16000)
                    .build();

            // Response handler collects transcript events
            StartStreamTranscriptionResponseHandler responseHandler =
                    StartStreamTranscriptionResponseHandler.builder()
                            .onResponse(r -> log.debug("Received initial response"))
                            .onError(e -> {
                                log.error("Transcription error: {}", e.getMessage(), e);
                                resultFuture.completeExceptionally(e);
                            })
                            .onComplete(() -> {
                                log.info("Transcription completed");
                                resultFuture.complete(null);
                            })
                            .subscriber(event -> {
                                if (event instanceof TranscriptEvent) {
                                    TranscriptEvent transcriptEvent = (TranscriptEvent) event;
                                    transcriptEvent.transcript().results().forEach(result -> {
                                        if (!result.isPartial()) {
                                            result.alternatives().forEach(alternative -> {
                                                String transcript = alternative.transcript();
                                                if (transcript != null && !transcript.trim().isEmpty()) {
                                                    transcriptBuilder.append(transcript).append(" ");
                                                    log.debug("Transcript fragment: {}", transcript);
                                                }
                                            });
                                        }
                                    });
                                }
                            })
                            .build();

            // Publisher that streams audioData in chunks as SDK AudioEvent objects
            final int CHUNK_SIZE = 3200; // ~100ms of 16kHz 16-bit mono audio (adjust if needed)
            SdkPublisher<AudioStream> audioStreamPublisher = new SdkPublisher<AudioStream>() {
                @Override
                public void subscribe(Subscriber<? super AudioStream> subscriber) {
                    subscriber.onSubscribe(new Subscription() {
                        volatile boolean cancelled = false;

                        @Override
                        public void request(long n) {
                            if (cancelled) {
                                return;
                            }
                            try {
                                int offset = 0;
                                while (offset < audioData.length && !cancelled) {
                                    int len = Math.min(CHUNK_SIZE, audioData.length - offset);
                                    byte[] chunk = Arrays.copyOfRange(audioData, offset, offset + len);
                                    AudioEvent audioEvent = AudioEvent.builder()
                                            .audioChunk(SdkBytes.fromByteArray(chunk))
                                            .build();
                                    subscriber.onNext(audioEvent);
                                    offset += len;
                                }
                                if (!cancelled) {
                                    subscriber.onComplete();
                                }
                            } catch (Throwable t) {
                                subscriber.onError(t);
                            }
                        }

                        @Override
                        public void cancel() {
                            cancelled = true;
                        }
                    });
                }
            };

            // Start transcription: streams audio and uses the response handler
            transcribeClient.startStreamTranscription(request, audioStreamPublisher, responseHandler);

            // Wait for transcription to complete (tunable timeout)
            resultFuture.get(60, TimeUnit.SECONDS);

            String finalTranscript = transcriptBuilder.toString().trim();
            log.info("Transcription complete: '{}'", finalTranscript);
            return finalTranscript;

        } catch (Exception e) {
            log.error("Transcription failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to transcribe audio: " + e.getMessage(), e);
        }
    }
}