package com.voiceai.conversation.config;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.retry.backoff.BackoffStrategy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.transcribestreaming.TranscribeStreamingAsyncClient;

import java.time.Duration;

/**
 * AWS service client configuration with production-grade retry and timeout settings.
 */
@Slf4j
@Configuration
public class AwsConfiguration {

    @Value("${aws.region}")
    private String awsRegion;

    @Value("${aws.api-timeout-seconds:30}")
    private int apiTimeoutSeconds;

    @Value("${aws.max-retries:3}")
    private int maxRetries;

    /**
     * Creates Amazon Polly client with retry policy and timeouts.
     */
    @Bean
    public PollyClient pollyClient() {
        log.info("Initializing Polly client for region: {}", awsRegion);

        return PollyClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .overrideConfiguration(createClientConfig())
                .build();
    }

    /**
     * Creates Amazon Transcribe Streaming async client with retry policy.
     */
    @Bean
    public TranscribeStreamingAsyncClient transcribeClient() {
        log.info("Initializing Transcribe Streaming client for region: {}", awsRegion);

        return TranscribeStreamingAsyncClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .overrideConfiguration(createClientConfig())
                .build();
    }

    /**
     * Creates Amazon Bedrock Runtime client for Claude model access.
     */
    @Bean
    public BedrockRuntimeClient bedrockRuntimeClient() {
        log.info("Initializing Bedrock Runtime client for region: {}", awsRegion);

        return BedrockRuntimeClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .overrideConfiguration(createClientConfig())
                .build();
    }

    /**
     * Creates standard client configuration with retry policy and timeouts.
     */
    private ClientOverrideConfiguration createClientConfig() {
        return ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofSeconds(apiTimeoutSeconds))
                .apiCallAttemptTimeout(Duration.ofSeconds(apiTimeoutSeconds / 2))
                .retryPolicy(RetryPolicy.builder()
                        .numRetries(maxRetries)
                        .backoffStrategy(BackoffStrategy.defaultStrategy())
                        .build())
                .build();
    }
}