package com.prioritask.worker;

import com.prioritask.queue.FifoTaskQueue;
import com.prioritask.queue.TaskQueue;
import com.prioritask.task.Task;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class WorkerTest {

    private Worker createWorker(TaskQueue queue, String name) {
        WorkerPool pool = new WorkerPool(queue, WorkerFactory.builder().build());
        return new Worker(queue, name, pool);
    }

    @Test
    void workerExecutesTaskFromQueue() throws InterruptedException {
        TaskQueue queue = new FifoTaskQueue(10);
        CountDownLatch latch = new CountDownLatch(1);
        queue.offer(Task.ofRunnable(latch::countDown));

        Worker worker = createWorker(queue, "test-worker");
        Thread thread = new Thread(worker);
        thread.start();

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        worker.shutdown();
        thread.interrupt();
        thread.join(1000);
    }

    @Test
    void workerCompletesMultipleTasks() throws InterruptedException {
        TaskQueue queue = new FifoTaskQueue(10);
        CountDownLatch latch = new CountDownLatch(3);
        for (int i = 0; i < 3; i++) {
            queue.offer(Task.ofRunnable(latch::countDown));
        }

        Worker worker = createWorker(queue, "multi-worker");
        Thread thread = new Thread(worker);
        thread.start();

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        worker.shutdown();
        thread.interrupt();
        thread.join(1000);
        assertEquals(3, worker.tasksCompleted());
    }

    @Test
    void workerContinuesAfterTaskException() throws InterruptedException {
        TaskQueue queue = new FifoTaskQueue(10);
        CountDownLatch latch = new CountDownLatch(1);
        queue.offer(Task.ofRunnable(() -> { throw new RuntimeException("fail"); }));
        queue.offer(Task.ofRunnable(latch::countDown));

        Worker worker = createWorker(queue, "exception-worker");
        Thread thread = new Thread(worker);
        thread.start();

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        worker.shutdown();
        thread.interrupt();
        thread.join(1000);
        assertEquals(2, worker.tasksCompleted());
    }

    @Test
    void shutdownStopsWorker() throws InterruptedException {
        TaskQueue queue = new FifoTaskQueue(10);
        Worker worker = createWorker(queue, "shutdown-worker");
        Thread thread = new Thread(worker);
        thread.start();

        worker.shutdown();
        thread.join(2000);
        assertFalse(thread.isAlive());
    }
}
