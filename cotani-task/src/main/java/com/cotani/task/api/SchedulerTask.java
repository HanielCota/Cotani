package com.cotani.task.api;

public interface SchedulerTask {

    boolean cancel();

    boolean cancelled();

    static SchedulerTask noop() {
        return NoopSchedulerTask.INSTANCE;
    }

    final class NoopSchedulerTask implements SchedulerTask {

        static final NoopSchedulerTask INSTANCE = new NoopSchedulerTask();

        private NoopSchedulerTask() {}

        @Override
        public boolean cancel() {
            return false;
        }

        @Override
        public boolean cancelled() {
            return false;
        }
    }
}
