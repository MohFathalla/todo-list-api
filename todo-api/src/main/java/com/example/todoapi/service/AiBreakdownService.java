package com.example.todoapi.service;

import com.example.todoapi.dto.AiBreakdownRequest;
import com.example.todoapi.dto.AiBreakdownResponse;

public interface AiBreakdownService {
    AiBreakdownResponse analyze(AiBreakdownRequest request);
}
