package com.prioritask.scheduler;

import com.prioritask.common.RejectedExecutionException;
import com.prioritask.core.LifecycleState;
import com.prioritask.core.TaskScheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class TimerScheduler {

    private final TaskScheduler executor;
    private final DelayQueue<DelayedTask> queue = new DelayQueue<>();
    private final AtomicReference<LifecycleState> state = new AtomicReference<>(LifecycleState.RUNNING);
    private final Thread schedulerThread;

    public TimerScheduler(TaskScheduler executor) {
        this.executor = executor;
        this.schedulerThread = new Thread(this::loop, "timer-scheduler");
        this.schedulerThread.setDaemon(true);
        this.schedulerThread.start();
    }

    private void loop() {
        while (state.get() == LifecycleState.RUNNING) {
            try {
                DelayedTask delayed = queue.take();
                if (!delayed.isCancelled()) {
                    executor.submit(() -> {
                        delayed.command().run();
                        delayed.markExecuted();
                    });
                } else {
                    delayed.markExecuted();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public ScheduledTaskHandle schedule(Runnable task, long delay, TimeUnit unit) {
        DelayedTask delayed = new DelayedTask(task, delay, unit);
        return scheduleDelayed(delayed);
    }

    public ScheduledTaskHandle scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        long periodNanos = unit.toNanos(period);
        FixedRateRunnable wrapper = new FixedRateRunnable(task, periodNanos, cancelled, queue);
        DelayedTask delayed = new DelayedTask(wrapper, initialDelay, unit);
        wrapper.setDelayedTask(delayed);
        return scheduleDelayed(delayed, cancelled);
    }

    public ScheduledTaskHandle scheduleWithFixedDelay(Runnable task, long initialDelay, long delay, TimeUnit unit) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        long delayNanos = unit.toNanos(delay);
        FixedDelayRunnable wrapper = new FixedDelayRunnable(task, delayNanos, cancelled, queue);
        DelayedTask delayed = new DelayedTask(wrapper, initialDelay, unit);
        wrapper.setDelayedTask(delayed);
        return scheduleDelayed(delayed, cancelled);
    }

    private ScheduledTaskHandle scheduleDelayed(DelayedTask delayed) {
        return scheduleDelayed(delayed, new AtomicBoolean(false));
    }

    private ScheduledTaskHandle scheduleDelayed(DelayedTask delayed, AtomicBoolean cancelled) {
        if (state.get() != LifecycleState.RUNNING) {
            throw new RejectedExecutionException("TimerScheduler is not running");
        }
        queue.put(delayed);
        return new ScheduledTaskHandle(delayed, cancelled);
    }

    public void shutdown() {
        LifecycleState prev = state.getAndUpdate(s ->
            s == LifecycleState.RUNNING ? LifecycleState.SHUTDOWN : s
        );
        if (prev == LifecycleState.RUNNING) {
            schedulerThread.interrupt();
            List<DelayedTask> remaining = new ArrayList<>();
            queue.drainTo(remaining);
            for (DelayedTask delayed : remaining) {
                if (!delayed.isCancelled()) {
                    executor.submit(delayed.command());
                }
            }
            state.set(LifecycleState.TERMINATED);
        }
    }

    public boolean isTerminated() {
        return state.get() == LifecycleState.TERMINATED;
    }

    private abstract static class RecurringRunnable implements Runnable {
        final Runnable delegate;
        final AtomicBoolean cancelled;
        final DelayQueue<DelayedTask> queue;

        RecurringRunnable(Runnable delegate, AtomicBoolean cancelled, DelayQueue<DelayedTask> queue) {
            this.delegate = delegate;
            this.cancelled = cancelled;
            this.queue = queue;
        }

        boolean isActive() {
            return !cancelled.get();
        }


    }

    private static class FixedRateRunnable extends RecurringRunnable {
        private final long periodNanos;
        private volatile DelayedTask currentDelayed;
        private long iteration;

        FixedRateRunnable(Runnable delegate, long periodNanos, AtomicBoolean cancelled, DelayQueue<DelayedTask> queue) {
            super(delegate, cancelled, queue);
            this.periodNanos = periodNanos;
        }

        void setDelayedTask(DelayedTask delayed) {
            this.currentDelayed = delayed;
        }

        @Override
        public void run() {
            if (cancelled.get() || currentDelayed == null) return;
            delegate.run();
            if (cancelled.get()) return;
            iteration++;
            long nextNanos = currentDelayed.scheduledNanos() + periodNanos * iteration;
            DelayedTask next = new DelayedTask(this, nextNanos);
            currentDelayed = next;
            queue.put(next);
        }
    }

    private static class FixedDelayRunnable extends RecurringRunnable {
        private final long delayNanos;
        private volatile DelayedTask currentDelayed;

        FixedDelayRunnable(Runnable delegate, long delayNanos, AtomicBoolean cancelled, DelayQueue<DelayedTask> queue) {
            super(delegate, cancelled, queue);
            this.delayNanos = delayNanos;
        }

        void setDelayedTask(DelayedTask delayed) {
            this.currentDelayed = delayed;
        }

        @Override
        public void run() {
            if (cancelled.get() || currentDelayed == null) return;
            delegate.run();
            if (cancelled.get()) return;
            long nextNanos = System.nanoTime() + delayNanos;
            DelayedTask next = new DelayedTask(this, nextNanos);
            currentDelayed = next;
            queue.put(next);
        }
    }
}
