package com.voiceai.conversation.service;

import com.voiceai.conversation.config.exception.ClassificationException;
import com.voiceai.conversation.config.exception.ServiceUnavailableException;
import com.voiceai.conversation.model.ClassificationResult;
import com.voiceai.conversation.model.Question;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for classifying user responses using Amazon Bedrock (Claude).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResponseClassifier {

    private final BedrockRuntimeClient bedrockClient;
    private final MetricsService metricsService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${aws.bedrock.model-id}")
    private String modelId;

    @Value("${aws.bedrock.max-tokens:500}")
    private int maxTokens;

    @Value("${aws.bedrock.temperature:0.3}")
    private double temperature;

    public ClassificationResult classifyResponse(Question question, String userResponse) {
        if (question == null || userResponse == null || userResponse.trim().isEmpty()) {
            throw new IllegalArgumentException("Question and response cannot be null or empty");
        }

        log.info("Classifying response for Q{}: '{}'", question.getId(), userResponse);
        long startTime = System.currentTimeMillis();

        try {
            String prompt = buildClassificationPrompt(question, userResponse);
            String response = invokeBedrockModel(prompt);
            ClassificationResult result = parseClassificationResult(response);

            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordClassificationLatency(duration);
            metricsService.incrementClassificationSuccess();

            log.info("Classification result for Q{}: matched={}, category={}, confidence={:.2f}",
                    question.getId(), result.isMatched(), result.getCategory(), result.getConfidence());

            return result;

        } catch (Exception e) {
            metricsService.incrementClassificationError();
            log.error("Classification failed for Q{}: {}", question.getId(), e.getMessage(), e);
            throw new ClassificationException("Failed to classify response", e);
        }
    }

    private String buildClassificationPrompt(Question question, String userResponse) {
        String categoriesStr = String.join(", ", question.getValidCategories());

        return String.format("""
            You are a health questionnaire response classifier. Your task is to determine if a user's spoken response matches one of the predefined valid categories for a question.
            
            Question: "%s"
            Valid categories: %s
            User's spoken response: "%s"
            
            Classification rules:
            1. Be flexible with synonyms (e.g., "pretty confident" matches "somewhat confident")
            2. Handle numbers as words or digits (e.g., "zero days" or "0" both match "0")
            3. Ignore filler words and minor variations
            4. If the response clearly maps to a category, set matched=true
            5. If ambiguous or doesn't match any category, set matched=false
            
            Respond with ONLY a JSON object in this exact format (no markdown, no preamble):
            {"matched": true/false, "category": "exact category string or null", "confidence": 0.0-1.0}
            
            Examples:
            - Response: "I felt quite confident" → {"matched": true, "category": "somewhat confident", "confidence": 0.85}
            - Response: "zero" → {"matched": true, "category": "0", "confidence": 0.95}
            - Response: "I don't know" → {"matched": false, "category": null, "confidence": 0.1}
            """,
                question.getText(),
                categoriesStr,
                userResponse
        );
    }

    private String invokeBedrockModel(String prompt) throws Exception {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("anthropic_version", "bedrock-2023-05-31");
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("temperature", temperature);
        requestBody.put("messages", List.of(
                Map.of("role", "user", "content", prompt)
        ));

        String requestJson = objectMapper.writeValueAsString(requestBody);

        InvokeModelRequest request = InvokeModelRequest.builder()
                .modelId(modelId)
                .body(SdkBytes.fromUtf8String(requestJson))
                .build();

        log.debug("Invoking Bedrock model: {}", modelId);
        InvokeModelResponse response = bedrockClient.invokeModel(request);

        String responseBody = response.body().asUtf8String();
        log.debug("Bedrock response: {}", responseBody);

        @SuppressWarnings("unchecked")
        Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) responseMap.get("content");

        if (content == null || content.isEmpty()) {
            throw new ServiceUnavailableException("Bedrock",
                    new RuntimeException("Empty response from model"));
        }

        return (String) content.get(0).get("text");
    }

    private ClassificationResult parseClassificationResult(String response)
            throws JsonProcessingException {
        String cleanJson = extractJson(response);
        return objectMapper.readValue(cleanJson, ClassificationResult.class);
    }

    private String extractJson(String response) {
        String cleaned = response.trim();

        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }

        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');

        if (start >= 0 && end > start) {
            return cleaned.substring(start, end + 1).trim();
        }

        return cleaned.trim();
    }
}
