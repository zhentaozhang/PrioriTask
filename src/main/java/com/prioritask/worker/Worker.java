package com.prioritask.worker;

import com.prioritask.common.TaskExceptionHandler;
import com.prioritask.common.TaskListener;
import com.prioritask.queue.TaskQueue;
import com.prioritask.task.Task;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

public class Worker implements Runnable {

    private final TaskQueue taskQueue;
    private final String name;
    private final LongAdder tasksCompleted = new LongAdder();
    private final WorkerPool pool;
    private final long keepAliveNanos;
    private final int batchSize;
    private volatile TaskListener listener;
    private volatile TaskExceptionHandler exceptionHandler;
    private volatile boolean running = true;
    private volatile boolean stopNow;
    private long lastTaskNanos = System.nanoTime();

    public Worker(TaskQueue taskQueue, String name, WorkerPool pool) {
        this(taskQueue, name, pool, 0, 32, pool.getTaskListener(), pool.getExceptionHandler());
    }

    public Worker(TaskQueue taskQueue, String name, WorkerPool pool, long keepAliveNanos) {
        this(taskQueue, name, pool, keepAliveNanos, 32, pool.getTaskListener(), pool.getExceptionHandler());
    }

    Worker(TaskQueue taskQueue, String name, WorkerPool pool, long keepAliveNanos,
           TaskListener listener, TaskExceptionHandler exceptionHandler) {
        this(taskQueue, name, pool, keepAliveNanos, 32, listener, exceptionHandler);
    }

    Worker(TaskQueue taskQueue, String name, WorkerPool pool, long keepAliveNanos, int batchSize,
           TaskListener listener, TaskExceptionHandler exceptionHandler) {
        this.taskQueue = taskQueue;
        this.name = name;
        this.pool = pool;
        this.keepAliveNanos = keepAliveNanos;
        this.batchSize = batchSize;
        this.listener = listener;
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    public void run() {
        List<Task<?>> batch = new ArrayList<>();
        while (running || (!stopNow && !taskQueue.isEmpty())) {
            try {
                Task<?> task = taskQueue.poll(500, TimeUnit.MILLISECONDS);
                if (task != null) {
                    batch.add(task);
                    taskQueue.drainTo(batch, batchSize - 1);
                    for (int i = 0; i < batch.size(); i++) {
                        executeTask(batch.get(i));
                    }
                    batch.clear();
                } else if (keepAliveNanos > 0 && running
                    && System.nanoTime() - lastTaskNanos >= keepAliveNanos) {
                    running = false;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (!stopNow && !taskQueue.isEmpty()) {
                    continue;
                }
                break;
            }
        }
    }

    private void executeTask(Task<?> task) {
        if (!task.markRunning()) {
            return;
        }
        // Listener/handler failures must never kill the worker; otherwise a
        // misbehaving observer would trigger restart churn in the pool.
        safeNotify(() -> listener.beforeExecute(Thread.currentThread(), task));
        if (keepAliveNanos > 0) {
            lastTaskNanos = System.nanoTime();
        }
        task.execute();
        tasksCompleted.increment();
        Throwable error = task.exception();
        if (error != null) {
            safeNotify(() -> exceptionHandler.onError(task, error));
        }
        safeNotify(() -> listener.afterExecute(task, error));
    }

    private static void safeNotify(Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            if (t instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void shutdown() {
        running = false;
    }

    void shutdownNow() {
        running = false;
        stopNow = true;
    }

    void updateListener(TaskListener listener) {
        this.listener = listener;
    }

    void updateExceptionHandler(TaskExceptionHandler handler) {
        this.exceptionHandler = handler;
    }

    public long tasksCompleted() {
        return tasksCompleted.sum();
    }

    public String workerName() {
        return name;
    }

    public boolean isRunning() {
        return running;
    }
}
