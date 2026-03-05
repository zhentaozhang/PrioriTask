package com.prioritask.worker;

import com.prioritask.queue.FifoTaskQueue;
import com.prioritask.queue.TaskQueue;
import com.prioritask.task.Task;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class WorkerPoolTest {

    @Test
    void poolStartsWorkers() throws InterruptedException {
        TaskQueue queue = new FifoTaskQueue(10);
        WorkerFactory factory = WorkerFactory.builder().namePrefix("pool-test").build();
        WorkerPool pool = new WorkerPool(queue, factory);

        pool.start(3);
        assertEquals(3, pool.poolSize());
        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.SECONDS);
    }

    @Test
    void poolExecutesTasks() throws InterruptedException {
        TaskQueue queue = new FifoTaskQueue(10);
        WorkerPool pool = new WorkerPool(queue, WorkerFactory.builder().build());

        CountDownLatch latch = new CountDownLatch(5);
        for (int i = 0; i < 5; i++) {
            queue.offer(Task.ofRunnable(latch::countDown));
        }

        pool.start(2);
        assertTrue(latch.await(3, TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(3, TimeUnit.SECONDS);
    }

    @Test
    void shutdownStopsAllWorkers() throws InterruptedException {
        TaskQueue queue = new FifoTaskQueue(10);
        WorkerPool pool = new WorkerPool(queue, WorkerFactory.builder().build());
        pool.start(2);

        pool.shutdown();
        boolean terminated = pool.awaitTermination(3, TimeUnit.SECONDS);
        assertTrue(terminated);
        assertEquals(0, pool.activeCount());
    }
}
