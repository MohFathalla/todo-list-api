package com.example.todoapi.service;

import com.example.todoapi.dto.TaskRequest;
import com.example.todoapi.dto.TaskResponse;
import com.example.todoapi.enums.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TaskService {
    // CR-002: idempotencyKey is null/blank when the client sends no Idempotency-Key header.
    TaskResponse create(TaskRequest request, String idempotencyKey);
    Page<TaskResponse> getAll(TaskStatus status, Long categoryId, Pageable pageable);
    TaskResponse getById(Long id);
    TaskResponse update(Long id, TaskRequest request);
    void delete(Long id);
}
