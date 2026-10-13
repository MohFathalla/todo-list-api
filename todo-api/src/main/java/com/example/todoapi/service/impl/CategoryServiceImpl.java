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
import com.example.todoapi.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Category business logic. delete() enforces the core rule from the brief:
 * a category can't be removed while it still has tasks.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final TaskRepository taskRepository;

    @Override
    public CategoryResponse create(CategoryRequest request) {
        requireNameAvailable(request.getName(), null);

        Category category = Category.builder()
                .name(request.getName())
                .build();
        Category saved = categoryRepository.save(category);
        return toResponse(saved, 0L);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAll() {
        // One grouped query for all task counts instead of one count query
        // per category (avoids N+1 queries on the list endpoint).
        Map<Long, Long> taskCounts = taskRepository.countTasksGroupedByCategory().stream()
                .collect(Collectors.toMap(CategoryTaskCount::categoryId, CategoryTaskCount::taskCount));

        return categoryRepository.findAll().stream()
                .map(category -> toResponse(category, taskCounts.getOrDefault(category.getId(), 0L)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getById(Long id) {
        Category category = findOrThrow(id);
        return toResponse(category, taskRepository.countByCategoryId(id));
    }

    @Override
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = findOrThrow(id);
        requireNameAvailable(request.getName(), id);

        category.setName(request.getName());
        Category saved = categoryRepository.save(category);
        return toResponse(saved, taskRepository.countByCategoryId(id));
    }

    @Override
    public void delete(Long id) {
        Category category = findOrThrow(id);

        // Core business rule: "A category shouldn't be deleted while it still has tasks in it."
        if (taskRepository.existsByCategoryId(id)) {
            throw new CategoryHasTasksException(
                    "Cannot delete category '" + category.getName() + "' because it still has tasks");
        }

        categoryRepository.delete(category);
    }

    /**
     * Rejects a name that's already used by another category (case-insensitive).
     * excludeId is null on create, or the category's own id on update (so
     * renaming a category to its current name is not treated as a conflict).
     */
    private void requireNameAvailable(String name, Long excludeId) {
        categoryRepository.findByNameIgnoreCase(name)
                .filter(existing -> !existing.getId().equals(excludeId))
                .ifPresent(existing -> {
                    throw new DuplicateCategoryNameException("Category name '" + name + "' is already in use");
                });
    }

    private Category findOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));
    }

    private CategoryResponse toResponse(Category category, long taskCount) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .taskCount(taskCount)
                .build();
    }
}
