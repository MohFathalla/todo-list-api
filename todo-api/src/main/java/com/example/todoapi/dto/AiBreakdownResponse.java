package com.example.todoapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiBreakdownResponse {
    private boolean shouldBreakDown;
    private String reason;
    private List<SuggestedTask> suggestedTasks;
}
