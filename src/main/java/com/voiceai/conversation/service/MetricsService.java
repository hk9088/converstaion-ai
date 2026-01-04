package com.voiceai.conversation.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Service for recording application metrics.
 * Integrates with Micrometer for Prometheus/Grafana monitoring.
 */
@Slf4j
@Service
public class MetricsService {

    private final Counter ttsSuccessCounter;
    private final Counter ttsErrorCounter;
    private final Counter sttSuccessCounter;
    private final Counter sttErrorCounter;
    private final Counter classificationSuccessCounter;
    private final Counter classificationErrorCounter;
    private final Timer ttsLatencyTimer;
    private final Timer sttLatencyTimer;
    private final Timer classificationLatencyTimer;

    public MetricsService(MeterRegistry registry) {
        this.ttsSuccessCounter = Counter.builder("questionnaire.tts.success")
                .description("Successful text-to-speech conversions")
                .register(registry);

        this.ttsErrorCounter = Counter.builder("questionnaire.tts.error")
                .description("Failed text-to-speech conversions")
                .register(registry);

        this.sttSuccessCounter = Counter.builder("questionnaire.stt.success")
                .description("Successful speech-to-text transcriptions")
                .register(registry);

        this.sttErrorCounter = Counter.builder("questionnaire.stt.error")
                .description("Failed speech-to-text transcriptions")
                .register(registry);

        this.classificationSuccessCounter = Counter.builder("questionnaire.classification.success")
                .description("Successful response classifications")
                .register(registry);

        this.classificationErrorCounter = Counter.builder("questionnaire.classification.error")
                .description("Failed response classifications")
                .register(registry);

        this.ttsLatencyTimer = Timer.builder("questionnaire.tts.latency")
                .description("Text-to-speech latency in milliseconds")
                .register(registry);

        this.sttLatencyTimer = Timer.builder("questionnaire.stt.latency")
                .description("Speech-to-text latency in milliseconds")
                .register(registry);

        this.classificationLatencyTimer = Timer.builder("questionnaire.classification.latency")
                .description("Classification latency in milliseconds")
                .register(registry);
    }

    public void incrementTtsSuccess() {
        ttsSuccessCounter.increment();
    }

    public void incrementTtsError() {
        ttsErrorCounter.increment();
    }

    public void incrementSttSuccess() {
        sttSuccessCounter.increment();
    }

    public void incrementSttError() {
        sttErrorCounter.increment();
    }

    public void incrementClassificationSuccess() {
        classificationSuccessCounter.increment();
    }

    public void incrementClassificationError() {
        classificationErrorCounter.increment();
    }

    public void recordTtsLatency(long milliseconds) {
        ttsLatencyTimer.record(milliseconds, TimeUnit.MILLISECONDS);
    }

    public void recordSttLatency(long milliseconds) {
        sttLatencyTimer.record(milliseconds, TimeUnit.MILLISECONDS);
    }

    public void recordClassificationLatency(long milliseconds) {
        classificationLatencyTimer.record(milliseconds, TimeUnit.MILLISECONDS);
    }
}
