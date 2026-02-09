package com.prioritask.task;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class Task<V> implements Comparable<Task<V>>, Future<V> {

    private static final AtomicLong idCounter = new AtomicLong(0);
    private final long taskId;
    private final Callable<V> callable;
    private final Runnable asRunnable;
    private final Priority priority;
    private final long submittedAt;
    private final AtomicReference<TaskState> state;

    private final CountDownLatch completed = new CountDownLatch(1);
    private volatile long startTime;
    private volatile long finishTime;
    private volatile V result;
    private volatile Throwable exception;

    private Task(Callable<V> callable, Priority priority) {
        this.taskId = idCounter.incrementAndGet();
        this.callable = callable;
        this.asRunnable = null;
        this.priority = priority;
        this.submittedAt = System.currentTimeMillis();
        this.state = new AtomicReference<>(TaskState.SUBMITTED);
    }

    private Task(Runnable runnable, Priority priority) {
        this.taskId = idCounter.incrementAndGet();
        this.callable = null;
        this.asRunnable = runnable;
        this.priority = priority;
        this.submittedAt = System.currentTimeMillis();
        this.state = new AtomicReference<>(TaskState.SUBMITTED);
    }

    public static <V> Task<V> of(Callable<V> callable) {
        return new Task<>(callable, Priority.defaultPriority());
    }

    public static <V> Task<V> of(Callable<V> callable, Priority priority) {
        return new Task<>(callable, priority);
    }

    public static Task<Void> ofRunnable(Runnable runnable) {
        return new Task<>(runnable, Priority.defaultPriority());
    }

    public static Task<Void> ofRunnable(Runnable runnable, Priority priority) {
        return new Task<>(runnable, priority);
    }

    public long taskId() { return taskId; }
    public Priority priority() { return priority; }
    public long submittedAt() { return submittedAt; }
    public long startTime() {
        long t = startTime;
        if (t == 0) {
            startTime = t = System.currentTimeMillis();
        }
        return t;
    }
    public long finishTime() { return finishTime; }
    public V resultNow() { return result; }
    public TaskState state() { return state.get(); }
    public Throwable exception() { return exception; }

    @Override
    public boolean isCancelled() {
        return state.get() == TaskState.CANCELLED;
    }

    @Override
    public boolean isDone() {
        return state.get().isTerminal();
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return cancel();
    }

    public boolean cancel() {
        while (true) {
            TaskState current = state.get();
            if (current.isTerminal()) return false;
            if (current == TaskState.RUNNING || current == TaskState.SUBMITTED) {
                if (state.compareAndSet(current, TaskState.CANCELLED)) {
                    completed.countDown();
                    return true;
                }
            } else {
                return false;
            }
        }
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
        awaitCompletion();
        return resultOrThrow();
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if (!awaitCompletion(timeout, unit)) {
            throw new TimeoutException("Task timed out");
        }
        return resultOrThrow();
    }

    private V resultOrThrow() throws ExecutionException {
        if (exception != null) throw new ExecutionException(exception);
        if (isCancelled()) throw new CancellationException();
        return result;
    }

    public TaskState markRunning() {
        if (!state.compareAndSet(TaskState.SUBMITTED, TaskState.RUNNING)) {
            throw new IllegalStateException("Cannot mark as running from state: " + state.get());
        }
        return TaskState.RUNNING;
    }

    public void awaitCompletion() throws InterruptedException {
        completed.await();
    }

    public boolean awaitCompletion(long timeout, TimeUnit unit) throws InterruptedException {
        return completed.await(timeout, unit);
    }

    public V execute() {
        try {
            if (state.get() == TaskState.CANCELLED) {
                completed.countDown();
                return null;
            }
            if (asRunnable != null) {
                asRunnable.run();
            } else {
                result = callable.call();
            }
            state.set(TaskState.COMPLETED);
            finishTime = System.currentTimeMillis();
            return result;
        } catch (Throwable e) {
            exception = e;
            state.set(TaskState.FAILED);
            finishTime = System.currentTimeMillis();
            return null;
        } finally {
            completed.countDown();
        }
    }

    @Override
    public int compareTo(Task<V> other) {
        return priority.compareToPriority(other.priority);
    }

    @Override
    public String toString() {
        return String.format("Task[id=%s, priority=%s, state=%s]", taskId, priority, state.get());
    }
}
