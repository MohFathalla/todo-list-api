package com.example.todoapi.service;

import com.example.todoapi.dto.CategoryRequest;
import com.example.todoapi.dto.CategoryResponse;

import java.util.List;

public interface CategoryService {
    CategoryResponse create(CategoryRequest request);
    List<CategoryResponse> getAll();
    CategoryResponse getById(Long id);
    CategoryResponse update(Long id, CategoryRequest request);
    void delete(Long id);
}

