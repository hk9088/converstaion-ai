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

            log.info("Classification result for Q{}: matched={}, category={}, confidence={}, retryMessage='{}'",
                    question.getId(), result.isMatched(), result.getCategory(),
                    result.getConfidence(), result.getRetryMessage());

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
            1. Synonyms and Variations: Be flexible with synonyms, informal expressions, and subtle variations. For example, "pretty confident" matches "somewhat confident," and "fairly confident" can be mapped to "somewhat confident" too. Ensure to handle common informal phrases, e.g., "quite confident," "fairly confident," and other similar expressions.
            2. American English and Slang: Consider variations in American English, informal language, and regional slang. For example, "kinda" for "kind of" or "gonna" for "going to" should be handled.
            3. Handling Numbers as Words or Digits: Be flexible with numbers written as words or digits. For example, "zero" should be mapped to "0," "one" to "1," and "10" to "ten." Also, handle ranges expressed in different formats (e.g., "1-3," "1 to 3," or "between 1 and 3" all map to the "1-3" range).
            4. Handling Minutes as a Fraction of a Day: If the question asks about days, treat minutes as a part of the day. For example, if the user mentions "20 minutes a day" in response to a question about how many days they engaged in activity, interpret it as "regularly."
            5. Filler Words: Ignore filler words like "um," "uh," "like," and minor variations. Only focus on the core response.
            6. Mixed / multi-part answers: If multiple categories are implied, select the most appropriate single option based on overall meaning (or default to the “middle” option unless strong evidence).
            7. Matching Criteria: If the response closely matches one of the predefined categories, set matched=true. If there is ambiguity or the response doesn't match any category, set matched=false.
            8. Ambiguity Handling: If the response is ambiguous or doesn't clearly fit a category, politely ask the user to try again. For example, if the user says "sometimes I walk" in response to a question about exercise, but the predefined options are more specific, gently explain that it doesn't match and encourage a more concise answer.
            9. Exercise Activity Mapping: If a user responds about an exercise activity other than the one mentioned in the question (e.g., running instead of walking), consider it as a sign of being quite active. Ask if they do this activity more frequently than the least option given (e.g., "Do you do this for more than 1-3 days a week?").
            10. Pay special attention to “not”, “never”, “don’t”, “wouldn’t”, “can’t” and resolve negation before mapping.
            9. Temporal Reasoning for Frequency Questions
            If a question asks about frequency over a time period (e.g., “over the past week”), evaluate the response across the entire time window, not just the most recent event.
            If the user mentions a specific event (e.g., injury, illness, accident) that occurred partway through the time period, assume a mixed condition.
            Classify based on the overall proportion of time the user implies they felt well.
            Do not default to extreme categories (“rarely”, “never”) unless the user clearly states they felt unwell for most or all of the time period.
            10. Partial-Week Heuristic (Implicit Day Mapping)
            When exact counts are not provided but timing is implied:
            Feeling well most days → “most of the time”
            Feeling well about half the time → “sometimes”
            Feeling well only briefly → “rarely”
            Feeling well not at all → “never”
            11. Event-Triggered Decline Rule
            If a user describes being active or well earlier in the period (e.g., exercising, working normally) and later becoming unwell due to a specific event, prioritize the earlier functional state when selecting a category.
            12. Clarification Threshold
            Ask a follow-up only if the response does not reasonably indicate how often the user felt well across the period.
            Do not ask follow-ups when a reasonable frequency can be inferred.
            Retry Message: • If the response is classified as unmatched, generate a short, natural, conversational retry message: Acknowledge their response kindly (e.g., "Thanks for your response!"). Gently explain why it doesn't match the expected format (e.g., "I didn't quite catch that."). Encourage them to try again with a concise response (e.g., "Could you please try again with one of the options?"). Get progressively more helpful with each retry. For example, if the user provides a vague answer multiple times, you can guide them towards the valid options explicitly (e.g., "Could you tell me how many days a week you engage in activity, such as walking?").
            Example Format for Responses:
            1. Matched Response: • Response: "I felt quite confident" {"matched": true, "category": "somewhat confident", "confidence": 0.85, "retryMessage": ""}
            2. Number Handling: • Response: "zero" {"matched": true, "category": "0", "confidence": 0.95, "retryMessage": ""}
            3. Ambiguous Response: • Response: "I don't know", "Thinking" {"matched": false, "category": null, "confidence": 0.1, "retryMessage": "I didn't quite catch that. Could you please choose one of these options: yes, no, sometimes, I don't take medication, or I don't have access to my medication?"}
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