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
    private final List<Worker> workers = new ArrayList<>();
    private final List<Thread> threads = new ArrayList<>();
    private final AtomicInteger activeCount = new AtomicInteger(0);
    private final long keepAliveNanos;
    private volatile TaskListener listener = new TaskListener() {};
    private volatile TaskExceptionHandler exceptionHandler = (t, e) -> {};
    private volatile boolean running = true;

    public WorkerPool(TaskQueue taskQueue, WorkerFactory workerFactory) {
        this(taskQueue, workerFactory, TimeUnit.SECONDS.toNanos(60));
    }

    public WorkerPool(TaskQueue taskQueue, WorkerFactory workerFactory, long keepAliveNanos) {
        this.taskQueue = taskQueue;
        this.workerFactory = workerFactory;
        this.keepAliveNanos = keepAliveNanos;
    }

    public TaskListener getTaskListener() {
        return listener;
    }

    public TaskExceptionHandler getExceptionHandler() {
        return exceptionHandler;
    }

    public void setTaskListener(TaskListener listener) {
        this.listener = listener;
        synchronized (workers) {
            for (Worker w : workers) {
                w.updateListener(listener);
            }
        }
    }

    public void setExceptionHandler(TaskExceptionHandler handler) {
        this.exceptionHandler = handler;
        synchronized (workers) {
            for (Worker w : workers) {
                w.updateExceptionHandler(handler);
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
        Worker worker = new Worker(taskQueue, "worker-" + (workers.size() + 1), this, keepAliveNanos,
                                   listener, exceptionHandler);
        Thread thread = workerFactory.newThread(() -> runWorker(worker));
        workers.add(worker);
        threads.add(thread);
        thread.start();
    }

    private void runWorker(Worker worker) {
        activeCount.incrementAndGet();
        try {
            worker.run();
        } catch (Exception t) {
            startWorker();
        } finally {
            activeCount.decrementAndGet();
        }
    }

    public void shutdown() {
        running = false;
        synchronized (workers) {
            for (Worker worker : workers) {
                worker.shutdown();
            }
        }
    }

    public void shutdownNow() {
        shutdown();
        synchronized (workers) {
            for (Thread thread : threads) {
                thread.interrupt();
            }
        }
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        List<Thread> snapshot;
        synchronized (workers) {
            snapshot = new ArrayList<>(threads);
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

    public int activeCount() {
        return activeCount.get();
    }

    public List<Worker> getWorkers() {
        synchronized (workers) {
            return new ArrayList<>(workers);
        }
    }
}
