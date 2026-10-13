package com.example.todoapi.repository;

import com.example.todoapi.entity.Task;
import com.example.todoapi.enums.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    boolean existsByCategoryId(Long categoryId);

    long countByCategoryId(Long categoryId);

    @Query("SELECT new com.example.todoapi.repository.CategoryTaskCount(t.category.id, COUNT(t)) " +
            "FROM Task t GROUP BY t.category.id")
    List<CategoryTaskCount> countTasksGroupedByCategory();

    Page<Task> findByStatus(TaskStatus status, Pageable pageable);

    Page<Task> findByCategoryId(Long categoryId, Pageable pageable);

    Page<Task> findByStatusAndCategoryId(TaskStatus status, Long categoryId, Pageable pageable);
}

