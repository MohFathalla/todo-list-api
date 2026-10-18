package com.example.todoapi.service.impl;

import com.example.todoapi.dto.AiBreakdownRequest;
import com.example.todoapi.dto.AiBreakdownResponse;
import com.example.todoapi.dto.SuggestedTask;
import com.example.todoapi.exception.AiProviderException;
import com.example.todoapi.service.AiBreakdownService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CR-005 (AI-Assisted Task Breakdown): builds a prompt, calls the provider,
 * parses its JSON reply, and drops any suggested due date that is in the
 * past or lands after the original task's own due date instead of failing
 * the whole response. No Task row is ever touched by this flow.
 */
@Service
@RequiredArgsConstructor
public class AiBreakdownServiceImpl implements AiBreakdownService {

    private static final String SYSTEM_PROMPT = """
            You are a project-planning assistant for a to-do list app.
            Given a task's title, description and due date, decide whether it
            should be broken down into smaller sub-tasks.

            Respond with ONLY a single JSON object, no markdown, no commentary,
            matching exactly this shape:
            {
              "shouldBreakDown": boolean,
              "reason": "short explanation",
              "suggestedTasks": [
                { "title": "string", "description": "string", "dueDate": "YYYY-MM-DD or null" }
              ]
            }

            If the task is already small and specific, set shouldBreakDown to
            false, explain why in "reason", and return an empty suggestedTasks
            array. Never invent a dueDate after the original task's due date.
            """;

    private final RestClient aiProviderRestClient;
    private final ObjectMapper objectMapper;

    @Value("${ai.provider.model}")
    private String model;

    @Override
    public AiBreakdownResponse analyze(AiBreakdownRequest request) {
        String rawContent = callProvider(buildUserPrompt(request));

        AiBreakdownResponse parsed;
        try {
            parsed = objectMapper.readValue(rawContent, AiBreakdownResponse.class);
        } catch (Exception e) {
            // Malformed/unparsable JSON is a provider failure, not "no breakdown needed".
            throw new AiProviderException("The AI provider returned an unexpected response format", e);
        }

        return sanitize(parsed, request.getDueDate());
    }

    private String callProvider(String userPrompt) {
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    "temperature", 0.2
            );

            JsonNode root = aiProviderRestClient.post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            String content = (root == null) ? null
                    : root.path("choices").path(0).path("message").path("content").asText(null);

            if (content == null || content.isBlank()) {
                throw new AiProviderException("The AI provider returned an empty response");
            }
            return content;
        } catch (RestClientException e) {
            throw new AiProviderException("Could not reach the AI provider", e);
        }
    }

    private String buildUserPrompt(AiBreakdownRequest request) {
        return """
                Today's date: %s
                Task title: %s
                Task description: %s
                Task due date: %s
                """.formatted(
                LocalDate.now(),
                request.getTitle(),
                request.getDescription() == null ? "(none)" : request.getDescription(),
                request.getDueDate() == null ? "(none)" : request.getDueDate()
        );
    }

    /**
     * Drops any suggested due date that is before today or after the
     * original task's own due date, instead of failing the whole response
     * (CR-005 §6 step 5 safeguard).
     */
    private AiBreakdownResponse sanitize(AiBreakdownResponse response, LocalDate originalDueDate) {
        if (response.getSuggestedTasks() == null) {
            response.setSuggestedTasks(List.of());
            return response;
        }

        LocalDate today = LocalDate.now();
        List<SuggestedTask> valid = new ArrayList<>();
        boolean droppedAny = false;

        for (SuggestedTask task : response.getSuggestedTasks()) {
            LocalDate due = task.getDueDate();
            boolean outOfRange = due != null
                    && (due.isBefore(today) || (originalDueDate != null && due.isAfter(originalDueDate)));
            if (outOfRange) {
                droppedAny = true;
            } else {
                valid.add(task);
            }
        }

        response.setSuggestedTasks(valid);
        if (droppedAny) {
            String note = "Some suggested due dates were out of range and were removed.";
            response.setReason(response.getReason() == null ? note : response.getReason() + " " + note);
        }
        return response;
    }
}
