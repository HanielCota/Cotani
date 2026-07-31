package net.cotani.metrics.binder;

import com.cotani.task.metrics.TaskMetricSnapshot;
import com.cotani.task.metrics.TaskMetrics;
import java.util.Objects;
import java.util.function.Supplier;
import net.cotani.metrics.api.MeterBinder;
import net.cotani.metrics.api.MetricsRegistry;

/**
 * Binds {@code cotani-task} execution statistics to a {@link MetricsRegistry}.
 */
public final class TaskMetricsBinder implements MeterBinder {
    private final String taskName;
    private final Supplier<TaskMetricSnapshot> snapshotSupplier;

    public TaskMetricsBinder(String taskName, Supplier<TaskMetricSnapshot> snapshotSupplier) {
        this.taskName = Objects.requireNonNull(taskName, "taskName");
        this.snapshotSupplier = Objects.requireNonNull(snapshotSupplier, "snapshotSupplier");
    }

    public TaskMetricsBinder(TaskMetrics taskMetrics, String taskName) {
        Objects.requireNonNull(taskMetrics, "taskMetrics");

        this.taskName = Objects.requireNonNull(taskName, "taskName");
        this.snapshotSupplier = () -> taskMetrics.snapshot(taskName);
    }

    @Override
    public void bindTo(MetricsRegistry registry) {
        Objects.requireNonNull(registry, "registry");

        registry.gauge("task.executions", () -> snapshotSupplier.get().executions(), "task", taskName);
        registry.gauge("task.failures", () -> snapshotSupplier.get().failures(), "task", taskName);
        registry.gauge(
                "task.execution_time.avg.ms",
                () -> snapshotSupplier.get().averageElapsed().toMillis(),
                "task",
                taskName);
    }
}
