package com.prioritask.scheduler;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScheduledTaskHandle {

    private final DelayedTask delayedTask;
    private final AtomicBoolean cancelled;
    private final boolean isRecurring;

    ScheduledTaskHandle(DelayedTask delayedTask) {
        this.delayedTask = delayedTask;
        this.cancelled = null;
        this.isRecurring = false;
    }

    ScheduledTaskHandle(DelayedTask delayedTask, AtomicBoolean cancelled) {
        this.delayedTask = delayedTask;
        this.cancelled = cancelled;
        this.isRecurring = true;
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
        if (delayedTask.isCancelled()) {
            return false;
        }
        if (!isRecurring && delayedTask.isExecuted()) {
            return false;
        }
        delayedTask.cancel();
        if (isRecurring) {
            cancelled.set(true);
        }
        return true;
    }

    public boolean isDone() {
        return delayedTask.isCancelled() || delayedTask.isExecuted();
    }

    public boolean isCancelled() {
        return delayedTask.isCancelled();
    }

    public Void get() throws InterruptedException, ExecutionException {
        if (isRecurring) {
            throw new UnsupportedOperationException("Cannot get() on a recurring scheduled task");
        }
        delayedTask.awaitExecution();
        if (isCancelled()) {
            throw new CancellationException();
        }
        return null;
    }

    public Void get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        if (isRecurring) {
            throw new UnsupportedOperationException("Cannot get() on a recurring scheduled task");
        }
        if (!delayedTask.awaitExecution(timeout, unit)) {
            throw new TimeoutException("Scheduled task not completed within timeout");
        }
        if (isCancelled()) {
            throw new CancellationException();
        }
        return null;
    }
}
