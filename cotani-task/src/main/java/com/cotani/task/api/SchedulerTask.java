package com.cotani.task.api;

public interface SchedulerTask {

    static SchedulerTask noop() {
        return NoopSchedulerTask.INSTANCE;
    }

    boolean cancel();

    boolean cancelled();

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
