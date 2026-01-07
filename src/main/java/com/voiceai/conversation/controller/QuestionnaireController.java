package com.voiceai.conversation.controller;

import com.voiceai.conversation.model.Question;
import com.voiceai.conversation.model.Session;
import com.voiceai.conversation.model.dto.QuestionResponse;
import com.voiceai.conversation.model.dto.ResponseSubmissionResult;
import com.voiceai.conversation.model.dto.SessionStartResponse;
import com.voiceai.conversation.service.QuestionnaireOrchestrator;
import com.voiceai.conversation.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API controller for voice questionnaire operations.
 */
@Slf4j
@RestController
@RequestMapping("/api/questionnaire")
@RequiredArgsConstructor
public class QuestionnaireController {

    private final QuestionnaireOrchestrator orchestrator;
    private final SessionService sessionService;
    private final List<Question> questions;

    @Value("${session.timeout-minutes:30}")
    private long sessionTimeoutMinutes;

    @PostMapping("/start")
    public ResponseEntity<SessionStartResponse> startSession() {
        log.info("Starting new questionnaire session");
        String sessionId = sessionService.createSession();

        SessionStartResponse response = new SessionStartResponse(
                sessionId,
                "Session started successfully",
                sessionTimeoutMinutes * 60
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/question/{sessionId}")
    public ResponseEntity<QuestionResponse> getCurrentQuestion(@PathVariable String sessionId) {
        log.info("Getting current question for session: {}", sessionId);

        Question question = orchestrator.getCurrentQuestion(sessionId);

        if (question == null) {
            return ResponseEntity.ok(new QuestionResponse(
                    null,
                    "Questionnaire completed",
                    true,
                    questions.size(),
                    questions.size()
            ));
        }

        Session session = sessionService.getSession(sessionId);

        return ResponseEntity.ok(new QuestionResponse(
                question,
                "Current question retrieved",
                false,
                questions.size(),
                session.getCurrentQuestionIndex() + 1
        ));
    }

    @GetMapping("/question/{sessionId}/audio")
    public ResponseEntity<byte[]> getQuestionAudio(@PathVariable String sessionId) {
        log.info("Getting question audio for session: {}", sessionId);

        byte[] audioData = orchestrator.getQuestionAudio(sessionId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("audio/mpeg"));
        headers.setContentLength(audioData.length);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=question.mp3");

        return new ResponseEntity<>(audioData, headers, HttpStatus.OK);
    }

    @GetMapping("/retry/{sessionId}/audio")
    public ResponseEntity<byte[]> getRetryAudio(
            @PathVariable String sessionId,
            @RequestParam String message) {
        log.info("Getting retry audio for session: {}", sessionId);

        byte[] audioData = orchestrator.getRetryAudio(message);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("audio/mpeg"));
        headers.setContentLength(audioData.length);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=retry.mp3");

        return new ResponseEntity<>(audioData, headers, HttpStatus.OK);
    }

    @PostMapping("/response/{sessionId}")
    public ResponseEntity<ResponseSubmissionResult> processVoiceResponse(
            @PathVariable String sessionId,
            @RequestParam("audio") MultipartFile audioFile) throws IOException {

        log.info("Processing voice response for session: {}", sessionId);

        if (audioFile.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    new ResponseSubmissionResult(
                            "FAILED",
                            "No audio file provided",
                            null,
                            null,
                            false,
                            0.0,
                            null,
                            false
                    )
            );
        }

        try {
            byte[] audioData = audioFile.getBytes();
            QuestionnaireOrchestrator.ProcessingResult result =
                    orchestrator.processVoiceResponse(sessionId, audioData);

            return ResponseEntity.ok(mapToDto(result));

        } catch (Exception e) {
            log.error("Error processing response: {}", e.getMessage(), e);
            throw e;
        }
    }

    @GetMapping("/responses/{sessionId}")
    public ResponseEntity<Map<Integer, String>> getSessionResponses(@PathVariable String sessionId) {
        log.info("Getting responses for session: {}", sessionId);

        Session session = sessionService.getSession(sessionId);
        Map<Integer, String> responses = session.getResponses().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().getClassifiedCategory()
                ));

        return ResponseEntity.ok(responses);
    }

    private ResponseSubmissionResult mapToDto(QuestionnaireOrchestrator.ProcessingResult result) {
        return new ResponseSubmissionResult(
                result.getStatus().name(),
                result.getMessage(),
                result.getTranscript(),
                result.getNextQuestion(),
                result.getStatus() == QuestionnaireOrchestrator.ProcessingStatus.COMPLETED,
                result.getClassification() != null ? result.getClassification().getConfidence() : 0.0,
                result.getRetryMessage(),
                result.getSession().isSuccessful()
        );
    }
}