package com.prioritask.core;

import com.prioritask.common.RejectedExecutionException;
import com.prioritask.common.RejectedExecutionHandler;
import com.prioritask.common.TaskExceptionHandler;
import com.prioritask.common.TaskListener;
import com.prioritask.queue.PriorityTaskQueue;
import com.prioritask.queue.TaskQueue;
import com.prioritask.task.Priority;
import com.prioritask.task.Task;
import com.prioritask.worker.WorkerFactory;
import com.prioritask.worker.WorkerPool;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class TaskScheduler {

    private final WorkerPool workerPool;
    private final TaskQueue taskQueue;
    private final AtomicReference<LifecycleState> state;
    private volatile RejectedExecutionHandler rejectedHandler;
    private volatile long shutdownTimeoutNanos = TimeUnit.MINUTES.toNanos(1);
    private volatile long shutdownNowTimeoutNanos = TimeUnit.SECONDS.toNanos(1);

    public TaskScheduler(int poolSize, int queueCapacity) {
        if (poolSize <= 0) {
            throw new IllegalArgumentException("poolSize must be positive: " + poolSize);
        }
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("queueCapacity must be positive: " + queueCapacity);
        }
        this.taskQueue = new PriorityTaskQueue(queueCapacity);
        this.workerPool = new WorkerPool(taskQueue, WorkerFactory.builder()
            .namePrefix("scheduler-worker")
            .build());
        this.state = new AtomicReference<>(LifecycleState.RUNNING);
        this.rejectedHandler = (task, scheduler) -> {
            throw new RejectedExecutionException("Task rejected by scheduler: " + task);
        };
        this.workerPool.start(poolSize);
    }

    public TaskScheduler(int poolSize) {
        this(poolSize, 100);
    }

    public void setRejectedHandler(RejectedExecutionHandler handler) {
        this.rejectedHandler = handler;
    }

    public void setTaskListener(TaskListener listener) {
        workerPool.setTaskListener(listener);
    }

    public void setExceptionHandler(TaskExceptionHandler handler) {
        workerPool.setExceptionHandler(handler);
    }

    public <V> Task<V> execute(Task<V> task) {
        if (state.get() != LifecycleState.RUNNING) {
            rejectedHandler.rejected(task, this);
            return null;
        }
        boolean accepted = taskQueue.offer(task);
        if (!accepted) {
            rejectedHandler.rejected(task, this);
            return null;
        }
        return task;
    }

    public <V> Task<V> submit(Callable<V> callable) {
        return execute(Task.of(callable));
    }

    public <V> Task<V> submit(Callable<V> callable, Priority priority) {
        return execute(Task.of(callable, priority));
    }

    public Task<Void> submit(Runnable runnable) {
        return execute(Task.ofRunnable(runnable));
    }

    public Task<Void> submit(Runnable runnable, Priority priority) {
        return execute(Task.ofRunnable(runnable, priority));
    }

    public void shutdown() {
        LifecycleState prev = state.getAndUpdate(s -> {
            if (s == LifecycleState.RUNNING) return LifecycleState.SHUTDOWN;
            return s;
        });
        if (prev == LifecycleState.RUNNING) {
            workerPool.shutdown();
            try {
                if (workerPool.awaitTermination(shutdownTimeoutNanos, TimeUnit.NANOSECONDS)) {
                    state.set(LifecycleState.TERMINATED);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                workerPool.shutdownNow();
            }
        }
    }

    public List<Task<?>> shutdownNow() {
        state.set(LifecycleState.STOP);
        workerPool.shutdownNow();
        List<Task<?>> remaining = taskQueue.drainTo();
        try {
            workerPool.awaitTermination(shutdownNowTimeoutNanos, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return remaining;
    }

    public void setShutdownTimeout(long timeout, TimeUnit unit) {
        this.shutdownTimeoutNanos = unit.toNanos(timeout);
    }

    public void setShutdownNowTimeout(long timeout, TimeUnit unit) {
        this.shutdownNowTimeoutNanos = unit.toNanos(timeout);
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return workerPool.awaitTermination(timeout, unit);
    }

    public boolean isTerminated() {
        return state.get() == LifecycleState.TERMINATED;
    }

    public LifecycleState state() {
        return state.get();
    }
}
