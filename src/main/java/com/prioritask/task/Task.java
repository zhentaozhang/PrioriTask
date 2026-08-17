package com.prioritask.task;

import java.util.Objects;
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
        return new Task<>(Objects.requireNonNull(callable, "callable"), Priority.defaultPriority());
    }

    public static <V> Task<V> of(Callable<V> callable, Priority priority) {
        return new Task<>(Objects.requireNonNull(callable, "callable"),
                          Objects.requireNonNull(priority, "priority"));
    }

    public static Task<Void> ofRunnable(Runnable runnable) {
        return new Task<>(Objects.requireNonNull(runnable, "runnable"), Priority.defaultPriority());
    }

    public static Task<Void> ofRunnable(Runnable runnable, Priority priority) {
        return new Task<>(Objects.requireNonNull(runnable, "runnable"),
                          Objects.requireNonNull(priority, "priority"));
    }

    public long taskId() { return taskId; }
    public Priority priority() { return priority; }
    public long submittedAt() { return submittedAt; }
    public long startTime() { return startTime; }
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
            if (current.isTerminal()) {
                return false;
            }
            // Only SUBMITTED and RUNNING are non-terminal; both may be cancelled.
            if (state.compareAndSet(current, TaskState.CANCELLED)) {
                completed.countDown();
                return true;
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

    public boolean markRunning() {
        if (!state.compareAndSet(TaskState.SUBMITTED, TaskState.RUNNING)) {
            return false;
        }
        startTime = System.currentTimeMillis();
        return true;
    }

    /**
     * Runs this task if it has not been cancelled.
     *
     * <p>State transitions are CAS-based so that a concurrent {@link #cancel()}
     * is never overwritten: once a task is CANCELLED it stays CANCELLED even if
     * the underlying callable/runnable completes or fails afterwards.
     *
     * @return the task result, or {@code null} if the task was cancelled
     */
    public V execute() {
        TaskState s = state.get();
        if (s.isTerminal()) {
            // Never re-run a terminal task. countDown() is idempotent here and
            // merely unblocks any concurrent waiter.
            completed.countDown();
            return s == TaskState.CANCELLED ? null : result;
        }
        // Direct callers may invoke execute() without markRunning(); promote SUBMITTED.
        if (s == TaskState.SUBMITTED) {
            state.compareAndSet(TaskState.SUBMITTED, TaskState.RUNNING);
        }
        try {
            if (asRunnable != null) {
                asRunnable.run();
            } else {
                result = callable.call();
            }
            finishTime = System.currentTimeMillis();
            // If a concurrent cancel() won the race, this CAS fails and the
            // task stays CANCELLED.
            state.compareAndSet(TaskState.RUNNING, TaskState.COMPLETED);
            return result;
        } catch (Throwable e) {
            exception = e;
            finishTime = System.currentTimeMillis();
            state.compareAndSet(TaskState.RUNNING, TaskState.FAILED);
            return null;
        } finally {
            completed.countDown();
        }
    }

    public void awaitCompletion() throws InterruptedException {
        completed.await();
    }

    public boolean awaitCompletion(long timeout, TimeUnit unit) throws InterruptedException {
        return completed.await(timeout, unit);
    }



    @Override
    public int compareTo(Task<V> other) {
        int cmp = priority.compareToPriority(other.priority);
        // Tie-break on submission order so that tasks with equal priority are
        // dequeued strictly FIFO (PriorityBlockingQueue is otherwise unstable
        // for equal elements).
        return cmp != 0 ? cmp : Long.compare(taskId, other.taskId);
    }

    @Override
    public String toString() {
        return String.format("Task[id=%s, priority=%s, state=%s]", taskId, priority, state.get());
    }
}
