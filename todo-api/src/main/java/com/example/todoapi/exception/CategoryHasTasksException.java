package com.example.todoapi.exception;

public class CategoryHasTasksException extends RuntimeException {
    public CategoryHasTasksException(String message) {
        super(message);
    }
}

