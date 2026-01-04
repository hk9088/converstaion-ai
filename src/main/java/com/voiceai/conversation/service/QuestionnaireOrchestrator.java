package com.voiceai.conversation.service;

import com.voiceai.conversation.model.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates the questionnaire flow including question progression,
 * retry logic, and response processing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionnaireOrchestrator {

    private final SessionService sessionService;
    private final SpeechToTextService speechToTextService;
    private final TextToSpeechService textToSpeechService;
    private final ResponseClassifier responseClassifier;
    private final AudioValidator audioValidator;
    private final List<Question> questions;

    @Value("${questionnaire.max-retries:3}")
    private int maxRetries;

    @Value("${questionnaire.confidence-threshold:0.6}")
    private double confidenceThreshold;

    public Question getCurrentQuestion(String sessionId) {
        Session session = sessionService.getSession(sessionId);

        if (session.getCurrentQuestionIndex() >= questions.size()) {
            session.complete();
            sessionService.saveSession(session);
            return null;
        }

        return questions.get(session.getCurrentQuestionIndex());
    }

    public byte[] getQuestionAudio(String sessionId) {
        Question question = getCurrentQuestion(sessionId);
        Session session = sessionService.getSession(sessionId);

        String questionText;
        if (question == null) {
            questionText = "Thank you for completing the questionnaire. Your responses have been recorded.";
        } else {
            questionText = formatQuestionText(question, session.getRetryCount());
        }

        return textToSpeechService.synthesizeSpeech(questionText);
    }

    public ProcessingResult processVoiceResponse(String sessionId, byte[] audioData) {
        audioValidator.validateAudio(audioData);

        Session session = sessionService.getSession(sessionId);
        Question currentQuestion = getCurrentQuestion(sessionId);

        if (currentQuestion == null) {
            return ProcessingResult.completed(session);
        }

        log.info("Processing response for session={}, question={}", sessionId, currentQuestion.getId());

        try {
            String transcript = speechToTextService.transcribeAudio(audioData);

            if (transcript.isEmpty()) {
                log.warn("Empty transcript for session={}", sessionId);
                return handleClassificationFailure(session, currentQuestion, "");
            }

            ClassificationResult classification = responseClassifier.classifyResponse(
                    currentQuestion,
                    transcript
            );

            if (classification.isValid(confidenceThreshold)) {
                return handleSuccessfulClassification(session, currentQuestion, classification, transcript);
            } else {
                log.info("Classification failed: matched={}, confidence={:.2f}",
                        classification.isMatched(), classification.getConfidence());
                return handleClassificationFailure(session, currentQuestion, transcript);
            }

        } catch (Exception e) {
            log.error("Error processing response: {}", e.getMessage(), e);
            return handleClassificationFailure(session, currentQuestion, "");
        }
    }

    private ProcessingResult handleSuccessfulClassification(
            Session session,
            Question question,
            ClassificationResult classification,
            String transcript) {

        UserResponse response = new UserResponse(
                question.getId(),
                transcript,
                classification.getCategory(),
                classification.getConfidence()
        );

        session.recordResponse(response);
        sessionService.saveSession(session);

        Question nextQuestion = getCurrentQuestion(session.getSessionId());

        log.info("Response recorded: Q{}={} (confidence={:.2f})",
                question.getId(), classification.getCategory(), classification.getConfidence());

        if (nextQuestion == null) {
            return ProcessingResult.completed(session, classification, transcript);
        } else {
            return ProcessingResult.success(session, classification, transcript, nextQuestion);
        }
    }

    private ProcessingResult handleClassificationFailure(
            Session session,
            Question question,
            String transcript) {

        session.incrementRetry();
        sessionService.saveSession(session);

        int retriesRemaining = maxRetries - session.getRetryCount();

        if (session.getRetryCount() >= maxRetries) {
            log.warn("Max retries exceeded for session={}, question={}",
                    session.getSessionId(), question.getId());
            return ProcessingResult.maxRetriesExceeded(session, question, transcript);
        }

        log.info("Retry {}/{} for session={}, question={}",
                session.getRetryCount(), maxRetries, session.getSessionId(), question.getId());

        return ProcessingResult.retry(session, question, transcript, retriesRemaining);
    }

    private String formatQuestionText(Question question, int retryCount) {
        if (retryCount == 0) {
            return question.getText();
        } else if (retryCount == 1) {
            return "I didn't quite catch that. Let me ask again. " + question.getText();
        } else {
            return "Let's try one more time. " + question.getText();
        }
    }

    @Data
    @AllArgsConstructor
    public static class ProcessingResult {
        private ProcessingStatus status;
        private Session session;
        private ClassificationResult classification;
        private String transcript;
        private Question nextQuestion;
        private String message;
        private int retriesRemaining;

        public static ProcessingResult success(
                Session session,
                ClassificationResult classification,
                String transcript,
                Question nextQuestion) {
            return new ProcessingResult(
                    ProcessingStatus.SUCCESS,
                    session,
                    classification,
                    transcript,
                    nextQuestion,
                    "Response recorded successfully",
                    0
            );
        }

        public static ProcessingResult retry(
                Session session,
                Question question,
                String transcript,
                int retriesRemaining) {
            return new ProcessingResult(
                    ProcessingStatus.RETRY,
                    session,
                    null,
                    transcript,
                    question,
                    "Please try answering again",
                    retriesRemaining
            );
        }

        public static ProcessingResult maxRetriesExceeded(
                Session session,
                Question question,
                String transcript) {
            return new ProcessingResult(
                    ProcessingStatus.MAX_RETRIES_EXCEEDED,
                    session,
                    null,
                    transcript,
                    question,
                    "Maximum retry attempts reached for this question",
                    0
            );
        }

        public static ProcessingResult completed(Session session) {
            return new ProcessingResult(
                    ProcessingStatus.COMPLETED,
                    session,
                    null,
                    null,
                    null,
                    "Questionnaire completed successfully",
                    0
            );
        }

        public static ProcessingResult completed(
                Session session,
                ClassificationResult classification,
                String transcript) {
            return new ProcessingResult(
                    ProcessingStatus.COMPLETED,
                    session,
                    classification,
                    transcript,
                    null,
                    "Questionnaire completed successfully",
                    0
            );
        }
    }

    public enum ProcessingStatus {
        SUCCESS,
        RETRY,
        MAX_RETRIES_EXCEEDED,
        COMPLETED
    }
}