package com.example.todoapi.service.impl;

import com.example.todoapi.dto.TaskRequest;
import com.example.todoapi.dto.TaskResponse;
import com.example.todoapi.entity.Category;
import com.example.todoapi.entity.Task;
import com.example.todoapi.enums.TaskStatus;
import com.example.todoapi.exception.ResourceNotFoundException;
import com.example.todoapi.repository.CategoryRepository;
import com.example.todoapi.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceImplTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private TaskServiceImpl taskService;

    private Category category(long id) {
        return Category.builder().id(id).name("Work").build();
    }

    private Task savedTask(long id, Category category, TaskStatus status) {
        return Task.builder().id(id).title("Title").status(status).category(category).build();
    }

    @Test
    void create_defaultsStatusToTodo_whenStatusOmitted() {
        Category category = category(1L);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        when(taskRepository.save(captor.capture())).thenAnswer(inv -> {
            Task t = inv.getArgument(0);
            t.setId(10L);
            return t;
        });

        TaskRequest request = new TaskRequest("Write report", null, null, null, 1L);
        TaskResponse response = taskService.create(request);

        assertThat(response.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(captor.getValue().getStatus()).isEqualTo(TaskStatus.TODO);
    }

    @Test
    void create_usesExplicitlyProvidedStatus() {
        Category category = category(1L);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        TaskRequest request = new TaskRequest("Write report", null, TaskStatus.IN_PROGRESS, null, 1L);
        TaskResponse response = taskService.create(request);

        assertThat(response.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void create_throwsNotFound_whenCategoryMissing() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        TaskRequest request = new TaskRequest("Title", null, null, null, 99L);

        assertThatThrownBy(() -> taskService.create(request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(taskRepository, never()).save(any());
    }

    @Test
    void update_defaultsStatusToTodo_whenOmitted_sameAsCreate() {
        Category category = category(1L);
        Task existing = savedTask(5L, category, TaskStatus.DONE);
        when(taskRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        // Status omitted on update: documents the consistent behavior - it
        // resets to TODO, the same default used on create, rather than
        // silently keeping the task's previous status.
        TaskRequest request = new TaskRequest("Updated title", null, null, null, 1L);
        TaskResponse response = taskService.update(5L, request);

        assertThat(response.getStatus()).isEqualTo(TaskStatus.TODO);
    }

    @Test
    void update_throwsNotFound_whenTaskMissing() {
        when(taskRepository.findById(5L)).thenReturn(Optional.empty());

        TaskRequest request = new TaskRequest("X", null, null, null, 1L);

        assertThatThrownBy(() -> taskService.update(5L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_throwsNotFound_whenTargetCategoryMissing() {
        Task existing = savedTask(5L, category(1L), TaskStatus.TODO);
        when(taskRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findById(2L)).thenReturn(Optional.empty());

        TaskRequest request = new TaskRequest("X", null, null, null, 2L);

        assertThatThrownBy(() -> taskService.update(5L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_movesTaskToTheRequestedCategory() {
        Category oldCategory = category(1L);
        Category newCategory = category(2L);
        Task existing = savedTask(5L, oldCategory, TaskStatus.TODO);
        when(taskRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(newCategory));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        TaskRequest request = new TaskRequest("X", null, TaskStatus.TODO, null, 2L);
        TaskResponse response = taskService.update(5L, request);

        assertThat(response.getCategoryId()).isEqualTo(2L);
    }

    @Test
    void getAll_filtersByStatusAndCategory_whenBothProvided() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Task> page = new PageImpl<>(List.of());
        when(taskRepository.findByStatusAndCategoryId(TaskStatus.TODO, 1L, pageable)).thenReturn(page);

        taskService.getAll(TaskStatus.TODO, 1L, pageable);

        verify(taskRepository).findByStatusAndCategoryId(TaskStatus.TODO, 1L, pageable);
        verify(taskRepository, never()).findByStatus(any(), any());
        verify(taskRepository, never()).findByCategoryId(any(), any());
    }

    @Test
    void getAll_filtersByStatusOnly() {
        Pageable pageable = PageRequest.of(0, 10);
        when(taskRepository.findByStatus(TaskStatus.DONE, pageable)).thenReturn(new PageImpl<>(List.of()));

        taskService.getAll(TaskStatus.DONE, null, pageable);

        verify(taskRepository).findByStatus(TaskStatus.DONE, pageable);
        verify(taskRepository, never()).findAll(pageable);
    }

    @Test
    void getAll_filtersByCategoryOnly() {
        Pageable pageable = PageRequest.of(0, 10);
        when(taskRepository.findByCategoryId(1L, pageable)).thenReturn(new PageImpl<>(List.of()));

        taskService.getAll(null, 1L, pageable);

        verify(taskRepository).findByCategoryId(1L, pageable);
        verify(taskRepository, never()).findAll(pageable);
    }

    @Test
    void getAll_noFilters_usesPlainFindAll() {
        Pageable pageable = PageRequest.of(0, 10);
        when(taskRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of()));

        taskService.getAll(null, null, pageable);

        verify(taskRepository).findAll(pageable);
    }

    @Test
    void delete_throwsNotFound_whenTaskMissing() {
        when(taskRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.delete(5L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_removesExistingTask() {
        Task existing = savedTask(5L, category(1L), TaskStatus.TODO);
        when(taskRepository.findById(5L)).thenReturn(Optional.of(existing));

        taskService.delete(5L);

        verify(taskRepository).delete(existing);
    }
}
