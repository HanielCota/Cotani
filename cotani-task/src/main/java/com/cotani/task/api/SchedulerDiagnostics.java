package com.cotani.task.api;

import com.cotani.task.metrics.TaskMetrics;

/** Exposes scheduler observability and failure-reporting contracts without dispatch capabilities. */
public interface SchedulerDiagnostics {

    TaskMetrics metrics();

    TaskExceptionHandler exceptionHandler();
}
