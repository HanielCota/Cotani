package com.cotani.task.throttle;

import java.time.Duration;

public interface RateLimiter {

    void acquire() throws InterruptedException;

    boolean tryAcquire();

    boolean tryAcquire(Duration timeout) throws InterruptedException;
}
