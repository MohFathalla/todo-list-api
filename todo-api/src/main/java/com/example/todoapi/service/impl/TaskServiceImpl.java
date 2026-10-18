package com.example.todoapi.service.impl;

import com.example.todoapi.dto.TaskRequest;
import com.example.todoapi.dto.TaskResponse;
import com.example.todoapi.entity.Category;
import com.example.todoapi.entity.IdempotencyRecord;
import com.example.todoapi.entity.Task;
import com.example.todoapi.enums.TaskStatus;
import com.example.todoapi.exception.IdempotencyKeyConflictException;
import com.example.todoapi.exception.ResourceNotFoundException;
import com.example.todoapi.repository.CategoryRepository;
import com.example.todoapi.repository.IdempotencyRecordRepository;
import com.example.todoapi.repository.TaskRepository;
import com.example.todoapi.service.TaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Optional;

/**
 * Task business logic: validates the linked category exists, applies
 * filtering/paging, and converts between entity and DTO.
 *
 * Update semantics (PUT /api/tasks/{id}): the request is treated as a full
 * replacement of title, description, dueDate and category, same as create.
 * An omitted status defaults to TODO in both create and update - applying
 * the same default consistently, rather than silently keeping the task's
 * previous status on update as before.
 *
 * CR-001 (Optimistic Locking): update() compares the client's expectedVersion
 * against the task's real current version and rejects a mismatch with a
 * 409 — it never overwrites the @Version field directly; Hibernate manages
 * that itself and increments it automatically on save().
 *
 * CR-002 (API Idempotency): create() accepts an optional client-supplied
 * idempotency key. See IdempotencyRecordRepository for the concurrency-safe
 * claim/release mechanism this relies on.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final CategoryRepository categoryRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ObjectMapper objectMapper;

    @Override
    public TaskResponse create(TaskRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return createTask(request);
        }

        String fingerprint = fingerprint(request);

        IdempotencyRecord claimAttempt = IdempotencyRecord.builder()
                .idempotencyKey(idempotencyKey)
                .requestFingerprint(fingerprint)
                .responseBody("{}") // placeholder until the task actually gets created below
                .responseStatus(0)
                .createdAt(Instant.now())
                .build();

        Optional<IdempotencyRecord> claimed = idempotencyRecordRepository.tryClaim(claimAttempt);

        if (claimed.isEmpty()) {
            // Someone already holds this key — replay their result, or reject a payload mismatch.
            return replayOrConflict(idempotencyKey, fingerprint);
        }

        try {
            TaskResponse response = createTask(request);

            IdempotencyRecord record = claimed.get();
            record.setResponseBody(writeJson(response));
            record.setResponseStatus(201);
            idempotencyRecordRepository.save(record);

            return response;
        } catch (RuntimeException ex) {
            // Task creation failed after we claimed the key (e.g. bad categoryId) —
            // release it so a corrected retry with the same key isn't blocked forever.
            idempotencyRecordRepository.releaseClaim(claimed.get().getId());
            throw ex;
        }
    }

    private TaskResponse replayOrConflict(String idempotencyKey, String fingerprint) {
        IdempotencyRecord existing = idempotencyRecordRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new IdempotencyKeyConflictException(
                        "Idempotency-Key '" + idempotencyKey + "' is still being processed. Please retry shortly."));

        if (existing.getResponseStatus() == 0) {
            // The winning request's claim is committed but it hasn't finished creating the task yet.
            throw new IdempotencyKeyConflictException(
                    "Idempotency-Key '" + idempotencyKey + "' is still being processed. Please retry shortly.");
        }

        if (!existing.getRequestFingerprint().equals(fingerprint)) {
            throw new IdempotencyKeyConflictException(
                    "Idempotency-Key '" + idempotencyKey + "' was already used with a different request body");
        }

        return readJson(existing.getResponseBody());
    }

    private TaskResponse createTask(TaskRequest request) {
        Category category = findCategoryOrThrow(request.getCategoryId());

        Task task = Task.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .status(resolveStatus(request.getStatus()))
                .dueDate(request.getDueDate())
                .category(category)
                .build();

        return toResponse(taskRepository.save(task));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TaskResponse> getAll(TaskStatus status, Long categoryId, Pageable pageable) {
        Page<Task> page;
        if (status != null && categoryId != null) {
            page = taskRepository.findByStatusAndCategoryId(status, categoryId, pageable);
        } else if (status != null) {
            page = taskRepository.findByStatus(status, pageable);
        } else if (categoryId != null) {
            page = taskRepository.findByCategoryId(categoryId, pageable);
        } else {
            page = taskRepository.findAll(pageable);
        }
        return page.map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public TaskResponse getById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Override
    @Transactional
    public TaskResponse update(Long id, TaskRequest request) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + id));

        // CR-001: compare what the client last saw against the real current version.
        // Don't touch task.setVersion() — Hibernate manages @Version itself and
        // increments it automatically on save().
        if (request.getExpectedVersion() == null
                || !request.getExpectedVersion().equals(task.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(Task.class, id);
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found with id: " + request.getCategoryId()));

        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setStatus(request.getStatus() != null ? request.getStatus() : TaskStatus.TODO);
        task.setDueDate(request.getDueDate());
        task.setCategory(category);

        Task saved = taskRepository.save(task);
        return toResponse(saved);
    }

    @Override
    public void delete(Long id) {
        Task task = findOrThrow(id);
        taskRepository.delete(task);
    }

    // Applied identically on create and update: an omitted status defaults to TODO.
    private TaskStatus resolveStatus(TaskStatus requested) {
        return requested != null ? requested : TaskStatus.TODO;
    }

    private Task findOrThrow(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + id));
    }

    private Category findCategoryOrThrow(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + categoryId));
    }

    private TaskResponse toResponse(Task task) {
        return TaskResponse.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .status(task.getStatus())
                .dueDate(task.getDueDate())
                .categoryId(task.getCategory().getId())
                .categoryName(task.getCategory().getName())
                .version(task.getVersion())
                .build();
    }

    // ---- CR-002 helpers ----

    private String fingerprint(TaskRequest request) {
        try {
            String json = objectMapper.writeValueAsString(request);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to fingerprint request for idempotency check", e);
        }
    }

    private String writeJson(TaskResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize response for idempotency record", e);
        }
    }

    private TaskResponse readJson(String json) {
        try {
            return objectMapper.readValue(json, TaskResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize stored idempotency response", e);
        }
    }
}
