package com.prioritask.scheduler;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

class DelayedTask implements Delayed {

    private final Runnable command;
    private final long scheduledNanos;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    DelayedTask(Runnable command, long delay, TimeUnit unit) {
        this.command = command;
        this.scheduledNanos = System.nanoTime() + unit.toNanos(delay);
    }

    DelayedTask(Runnable command, long scheduledNanos) {
        this.command = command;
        this.scheduledNanos = scheduledNanos;
    }

    Runnable command() {
        return command;
    }

    boolean isCancelled() {
        return cancelled.get();
    }

    void cancel() {
        cancelled.set(true);
    }

    long scheduledNanos() {
        return scheduledNanos;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(scheduledNanos - System.nanoTime(), TimeUnit.NANOSECONDS);
    }

    @Override
    public int compareTo(Delayed other) {
        long diff = this.getDelay(TimeUnit.NANOSECONDS) - other.getDelay(TimeUnit.NANOSECONDS);
        return diff < 0 ? -1 : (diff > 0 ? 1 : 0);
    }
}
