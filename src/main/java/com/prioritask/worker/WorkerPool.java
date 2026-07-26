package com.prioritask.worker;

import com.prioritask.common.TaskExceptionHandler;
import com.prioritask.common.TaskListener;
import com.prioritask.queue.TaskQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class WorkerPool {

    private final TaskQueue taskQueue;
    private final WorkerFactory workerFactory;
    private final List<WorkerEntry> workers = new ArrayList<>();
    private final AtomicInteger activeCount = new AtomicInteger(0);
    private final long keepAliveNanos;
    private final int batchSize;
    private int restartCount;
    private static final int MAX_RESTARTS = 10;
    private volatile TaskListener listener = new TaskListener() {};
    private volatile TaskExceptionHandler exceptionHandler = (t, e) -> {};
    private volatile boolean running = true;

    public WorkerPool(TaskQueue taskQueue, WorkerFactory workerFactory) {
        this(taskQueue, workerFactory, TimeUnit.SECONDS.toNanos(60), 32);
    }

    public WorkerPool(TaskQueue taskQueue, WorkerFactory workerFactory, long keepAliveNanos) {
        this(taskQueue, workerFactory, keepAliveNanos, 32);
    }

    public WorkerPool(TaskQueue taskQueue, WorkerFactory workerFactory, long keepAliveNanos, int batchSize) {
        this.taskQueue = taskQueue;
        this.workerFactory = workerFactory;
        this.keepAliveNanos = keepAliveNanos;
        this.batchSize = batchSize;
    }

    public TaskListener getTaskListener() {
        return listener;
    }

    public TaskExceptionHandler getExceptionHandler() {
        return exceptionHandler;
    }

    public void setTaskListener(TaskListener listener) {
        TaskListener safe = listener != null ? listener : new TaskListener() {};
        this.listener = safe;
        synchronized (workers) {
            for (WorkerEntry entry : workers) {
                entry.worker.updateListener(safe);
            }
        }
    }

    public void setExceptionHandler(TaskExceptionHandler handler) {
        TaskExceptionHandler safe = handler != null ? handler : (t, e) -> {};
        this.exceptionHandler = safe;
        synchronized (workers) {
            for (WorkerEntry entry : workers) {
                entry.worker.updateExceptionHandler(safe);
            }
        }
    }

    public void start(int count) {
        synchronized (workers) {
            for (int i = 0; i < count; i++) {
                startWorkerLocked();
            }
        }
    }

    public void startWorker() {
        synchronized (workers) {
            startWorkerLocked();
        }
    }

    private void startWorkerLocked() {
        if (!running) return;
        Worker worker = new Worker(taskQueue, "worker-" + (workers.size() + 1), this, keepAliveNanos, batchSize,
                                   listener, exceptionHandler);
        Thread thread = workerFactory.newThread(() -> runWorker(worker));
        workers.add(new WorkerEntry(worker, thread));
        thread.start();
    }

    private void runWorker(Worker worker) {
        activeCount.incrementAndGet();
        try {
            worker.run();
        } catch (Exception t) {
            synchronized (workers) {
                if (++restartCount < MAX_RESTARTS) {
                    startWorkerLocked();
                }
            }
        } finally {
            activeCount.decrementAndGet();
        }
    }

    public void shutdown() {
        running = false;
        synchronized (workers) {
            for (WorkerEntry entry : workers) {
                entry.worker.shutdown();
                entry.thread.interrupt();
            }
        }
    }

    public void shutdownNow() {
        running = false;
        synchronized (workers) {
            for (WorkerEntry entry : workers) {
                entry.worker.shutdown();
                entry.thread.interrupt();
            }
        }
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        List<Thread> snapshot;
        synchronized (workers) {
            snapshot = workers.stream().map(e -> e.thread).toList();
        }
        for (Thread thread : snapshot) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                thread.join(1);
            } else {
                thread.join(remaining / 1_000_000, (int)(remaining % 1_000_000));
            }
        }
        return snapshot.stream().noneMatch(Thread::isAlive);
    }

    public int poolSize() {
        synchronized (workers) {
            return workers.size();
        }
    }

    public List<Worker> getWorkers() {
        synchronized (workers) {
            return workers.stream().map(e -> e.worker).toList();
        }
    }

    public int activeCount() {
        return activeCount.get();
    }

    private record WorkerEntry(Worker worker, Thread thread) {}
}
