package com.example.todoapi.controller;

import com.example.todoapi.dto.AiBreakdownRequest;
import com.example.todoapi.dto.AiBreakdownResponse;
import com.example.todoapi.service.AiBreakdownService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@Tag(name = "Tasks", description = "Manage to-do tasks within a category")
public class AiBreakdownController {

    private final AiBreakdownService aiBreakdownService;

    @PostMapping("/ai-breakdown")
    @Operation(summary = "Ask the AI whether a task should be broken down into smaller sub-tasks",
            description = "Stateless - no Task row is ever created or modified here. "
                    + "Submit any approved suggestion through the normal POST /api/tasks.")
    public ResponseEntity<AiBreakdownResponse> analyze(@Valid @RequestBody AiBreakdownRequest request) {
        return ResponseEntity.ok(aiBreakdownService.analyze(request));
    }
}
