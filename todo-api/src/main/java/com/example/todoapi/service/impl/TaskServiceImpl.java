package com.example.todoapi.service.impl;

import com.example.todoapi.dto.TaskRequest;
import com.example.todoapi.dto.TaskResponse;
import com.example.todoapi.entity.Category;
import com.example.todoapi.entity.Task;
import com.example.todoapi.enums.TaskStatus;
import com.example.todoapi.exception.ResourceNotFoundException;
import com.example.todoapi.repository.CategoryRepository;
import com.example.todoapi.repository.TaskRepository;
import com.example.todoapi.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Task business logic: validates the linked category exists, applies
 * filtering/paging, and converts between entity and DTO.
 *
 * Update semantics (PUT /api/tasks/{id}): the request is treated as a full
 * replacement of title, description, dueDate and category, same as create.
 * An omitted status defaults to TODO in both create and update - applying
 * the same default consistently, rather than silently keeping the task's
 * previous status on update as before.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final CategoryRepository categoryRepository;

    @Override
    public TaskResponse create(TaskRequest request) {
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
    public TaskResponse update(Long id, TaskRequest request) {
        Task task = findOrThrow(id);
        Category category = findCategoryOrThrow(request.getCategoryId());

        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setStatus(resolveStatus(request.getStatus()));
        task.setDueDate(request.getDueDate());
        task.setCategory(category);

        return toResponse(taskRepository.save(task));
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
                .build();
    }
}
