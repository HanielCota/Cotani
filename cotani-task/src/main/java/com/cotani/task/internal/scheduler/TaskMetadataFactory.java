package com.cotani.task.internal.scheduler;

import com.cotani.task.api.ExecutionTarget;
import com.cotani.task.api.TaskMetadata;

@FunctionalInterface
interface TaskMetadataFactory {
    TaskMetadata create(String name, ExecutionTarget target);
}
