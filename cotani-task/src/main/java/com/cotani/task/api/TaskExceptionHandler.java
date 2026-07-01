package com.cotani.task.api;

@FunctionalInterface
public interface TaskExceptionHandler {

    void handle(TaskContext context, Throwable throwable);
}
