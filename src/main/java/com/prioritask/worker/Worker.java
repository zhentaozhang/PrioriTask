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
    private TaskListener listener;
    private TaskExceptionHandler exceptionHandler;
    private volatile boolean running = true;
    private long lastTaskNanos = System.nanoTime();

    public Worker(TaskQueue taskQueue, String name, WorkerPool pool) {
        this(taskQueue, name, pool, 0, pool.getTaskListener(), pool.getExceptionHandler());
    }

    public Worker(TaskQueue taskQueue, String name, WorkerPool pool, long keepAliveNanos) {
        this(taskQueue, name, pool, keepAliveNanos, pool.getTaskListener(), pool.getExceptionHandler());
    }

    Worker(TaskQueue taskQueue, String name, WorkerPool pool, long keepAliveNanos,
           TaskListener listener, TaskExceptionHandler exceptionHandler) {
        this.taskQueue = taskQueue;
        this.name = name;
        this.pool = pool;
        this.keepAliveNanos = keepAliveNanos;
        this.listener = listener;
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    public void run() {
        List<Task<?>> batch = new ArrayList<>();
        while (running || !taskQueue.isEmpty()) {
            try {
                Task<?> task = taskQueue.poll(500, TimeUnit.MILLISECONDS);
                if (task != null) {
                    batch.add(task);
                    taskQueue.drainTo(batch, 31);
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
                if (!taskQueue.isEmpty()) {
                    continue;
                }
                break;
            }
        }
    }

    private void executeTask(Task<?> task) {
        task.markRunning();
        listener.beforeExecute(Thread.currentThread(), task);
        if (keepAliveNanos > 0) {
            lastTaskNanos = System.nanoTime();
        }
        task.execute();
        tasksCompleted.increment();
        Throwable error = task.exception();
        if (error != null) {
            exceptionHandler.onError(task, error);
        }
        listener.afterExecute(task, error);
    }

    public void shutdown() {
        running = false;
    }

    void updateListener(TaskListener listener) {
        this.listener = listener;
    }

    void updateExceptionHandler(TaskExceptionHandler handler) {
        this.exceptionHandler = handler;
    }

    public int tasksCompleted() {
        return (int) tasksCompleted.sum();
    }

    public String workerName() {
        return name;
    }

    public boolean isRunning() {
        return running;
    }
}
