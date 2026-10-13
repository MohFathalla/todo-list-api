package com.example.todoapi.service.impl;

import com.example.todoapi.dto.CategoryRequest;
import com.example.todoapi.dto.CategoryResponse;
import com.example.todoapi.entity.Category;
import com.example.todoapi.exception.CategoryHasTasksException;
import com.example.todoapi.exception.DuplicateCategoryNameException;
import com.example.todoapi.exception.ResourceNotFoundException;
import com.example.todoapi.repository.CategoryRepository;
import com.example.todoapi.repository.CategoryTaskCount;
import com.example.todoapi.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    @Test
    void create_savesCategoryWithZeroTaskCount() {
        when(categoryRepository.findByNameIgnoreCase("Work")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class)))
                .thenAnswer(inv -> {
                    Category c = inv.getArgument(0);
                    c.setId(1L);
                    return c;
                });

        CategoryResponse response = categoryService.create(new CategoryRequest("Work"));

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Work");
        assertThat(response.getTaskCount()).isZero();
    }

    @Test
    void create_rejectsDuplicateNameCaseInsensitive() {
        Category existing = Category.builder().id(1L).name("Work").build();
        when(categoryRepository.findByNameIgnoreCase("WORK")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> categoryService.create(new CategoryRequest("WORK")))
                .isInstanceOf(DuplicateCategoryNameException.class);

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void update_throwsNotFound_whenCategoryMissing() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.update(99L, new CategoryRequest("New")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_allowsKeepingItsOwnCurrentName() {
        Category category = Category.builder().id(1L).name("Work").build();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.findByNameIgnoreCase("Work")).thenReturn(Optional.of(category));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));
        when(taskRepository.countByCategoryId(1L)).thenReturn(0L);

        CategoryResponse response = categoryService.update(1L, new CategoryRequest("Work"));

        assertThat(response.getName()).isEqualTo("Work");
    }

    @Test
    void update_rejectsRenamingToAnotherExistingCategoryName() {
        Category category = Category.builder().id(1L).name("Work").build();
        Category other = Category.builder().id(2L).name("Personal").build();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.findByNameIgnoreCase("Personal")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> categoryService.update(1L, new CategoryRequest("Personal")))
                .isInstanceOf(DuplicateCategoryNameException.class);

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void getAll_usesOneGroupedQuery_insteadOfPerCategoryCounts() {
        Category work = Category.builder().id(1L).name("Work").build();
        Category personal = Category.builder().id(2L).name("Personal").build();
        when(categoryRepository.findAll()).thenReturn(List.of(work, personal));
        when(taskRepository.countTasksGroupedByCategory())
                .thenReturn(List.of(new CategoryTaskCount(1L, 3L)));

        List<CategoryResponse> responses = categoryService.getAll();

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getTaskCount()).isEqualTo(3L);
        assertThat(responses.get(1).getTaskCount()).isZero(); // no tasks -> not in the grouped result

        // The whole point of the fix: one grouped query, never a per-category lookup.
        verify(taskRepository, times(1)).countTasksGroupedByCategory();
        verify(taskRepository, never()).countByCategoryId(anyLong());
        verify(taskRepository, never()).findByCategoryId(anyLong(), any());
    }

    @Test
    void getById_usesCheapCountQuery_notUnpagedTaskFetch() {
        Category category = Category.builder().id(1L).name("Work").build();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(taskRepository.countByCategoryId(1L)).thenReturn(5L);

        CategoryResponse response = categoryService.getById(1L);

        assertThat(response.getTaskCount()).isEqualTo(5L);
        verify(taskRepository, never()).findByCategoryId(anyLong(), any());
    }

    @Test
    void delete_rejectsCategoryThatStillHasTasks() {
        Category category = Category.builder().id(1L).name("Work").build();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(taskRepository.existsByCategoryId(1L)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.delete(1L))
                .isInstanceOf(CategoryHasTasksException.class);

        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void delete_removesCategoryWithNoTasks() {
        Category category = Category.builder().id(1L).name("Work").build();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(taskRepository.existsByCategoryId(1L)).thenReturn(false);

        categoryService.delete(1L);

        verify(categoryRepository).delete(category);
    }
}
