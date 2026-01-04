package com.voiceai.conversation.service;


import com.voiceai.conversation.config.exception.ServiceUnavailableException;
import com.voiceai.conversation.config.exception.TranscriptionException;
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
import java.util.concurrent.TimeoutException;

/**
 * Service for speech-to-text transcription using Amazon Transcribe Streaming.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpeechToTextService {

    private static final int CHUNK_SIZE = 3200;
    private static final int TRANSCRIPTION_TIMEOUT_SECONDS = 60;

    private final TranscribeStreamingAsyncClient transcribeClient;
    private final MetricsService metricsService;

    public String transcribeAudio(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            throw new IllegalArgumentException("Audio data cannot be empty");
        }

        log.info("Starting transcription for {} bytes", audioData.length);
        long startTime = System.currentTimeMillis();

        StringBuilder transcriptBuilder = new StringBuilder();
        CompletableFuture<Void> resultFuture = new CompletableFuture<>();

        try {
            StartStreamTranscriptionRequest request = StartStreamTranscriptionRequest.builder()
                    .languageCode(LanguageCode.EN_US)
                    .mediaEncoding(MediaEncoding.PCM)
                    .mediaSampleRateHertz(16000)
                    .build();

            StartStreamTranscriptionResponseHandler responseHandler =
                    createResponseHandler(transcriptBuilder, resultFuture);

            SdkPublisher<AudioStream> audioPublisher = createAudioPublisher(audioData);

            transcribeClient.startStreamTranscription(request, audioPublisher, responseHandler);

            resultFuture.get(TRANSCRIPTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            String transcript = transcriptBuilder.toString().trim();
            long duration = System.currentTimeMillis() - startTime;

            metricsService.recordSttLatency(duration);
            metricsService.incrementSttSuccess();

            log.info("Transcription complete: '{}' ({}ms)", transcript, duration);
            return transcript;

        } catch (TimeoutException e) {
            metricsService.incrementSttError();
            log.error("Transcription timeout after {}s", TRANSCRIPTION_TIMEOUT_SECONDS);
            throw new TranscriptionException("Transcription timeout", e);
        } catch (Exception e) {
            metricsService.incrementSttError();
            log.error("Transcription failed: {}", e.getMessage(), e);
            throw new ServiceUnavailableException("Speech-to-Text", e);
        }
    }

    private StartStreamTranscriptionResponseHandler createResponseHandler(
            StringBuilder transcriptBuilder,
            CompletableFuture<Void> resultFuture) {

        return StartStreamTranscriptionResponseHandler.builder()
                .onResponse(r -> log.debug("Transcription started"))
                .onError(e -> {
                    log.error("Transcription error: {}", e.getMessage());
                    resultFuture.completeExceptionally(e);
                })
                .onComplete(() -> {
                    log.debug("Transcription stream completed");
                    resultFuture.complete(null);
                })
                .subscriber(event -> handleTranscriptEvent(event, transcriptBuilder))
                .build();
    }

    private void handleTranscriptEvent(TranscriptResultStream event, StringBuilder builder) {
        if (event instanceof TranscriptEvent) {
            TranscriptEvent transcriptEvent = (TranscriptEvent) event;
            transcriptEvent.transcript().results().forEach(result -> {
                if (!result.isPartial()) {
                    result.alternatives().forEach(alternative -> {
                        String text = alternative.transcript();
                        if (text != null && !text.trim().isEmpty()) {
                            builder.append(text).append(" ");
                            log.debug("Transcript fragment: {}", text);
                        }
                    });
                }
            });
        }
    }

    private SdkPublisher<AudioStream> createAudioPublisher(byte[] audioData) {
        return new SdkPublisher<AudioStream>() {
            @Override
            public void subscribe(Subscriber<? super AudioStream> subscriber) {
                subscriber.onSubscribe(new Subscription() {
                    private volatile boolean cancelled = false;

                    @Override
                    public void request(long n) {
                        if (cancelled) return;

                        try {
                            streamAudioChunks(subscriber);
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

                    private void streamAudioChunks(Subscriber<? super AudioStream> subscriber) {
                        int offset = 0;
                        while (offset < audioData.length && !cancelled) {
                            int length = Math.min(CHUNK_SIZE, audioData.length - offset);
                            byte[] chunk = Arrays.copyOfRange(audioData, offset, offset + length);

                            AudioEvent audioEvent = AudioEvent.builder()
                                    .audioChunk(SdkBytes.fromByteArray(chunk))
                                    .build();

                            subscriber.onNext(audioEvent);
                            offset += length;
                        }
                    }
                });
            }
        };
    }
}
