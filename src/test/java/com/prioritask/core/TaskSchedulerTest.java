package com.prioritask.core;

import com.prioritask.task.Task;
import com.prioritask.common.RejectedExecutionException;
import com.prioritask.task.Priority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class TaskSchedulerTest {

    private TaskScheduler scheduler;

    @AfterEach
    void tearDown() {
        if (scheduler != null && !scheduler.isTerminated()) {
            scheduler.shutdownNow();
        }
    }

    @Test
    void executeRunnableTask() throws Exception {
        scheduler = new TaskScheduler(2, 10);
        CountDownLatch latch = new CountDownLatch(1);
        Task<Void> future = scheduler.execute(Task.ofRunnable(latch::countDown));
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(future.isDone());
    }

    @Test
    void submitCallableReturnsResult() throws Exception {
        scheduler = new TaskScheduler(2, 10);
        Task<String> future = scheduler.submit(() -> "hello");
        assertEquals("hello", future.get(3, TimeUnit.SECONDS));
    }

    @Test
    void submitRunnableReturnsFuture() throws Exception {
        scheduler = new TaskScheduler(2, 10);
        CountDownLatch latch = new CountDownLatch(1);
        Task<Void> future = scheduler.submit(latch::countDown);
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(future.isDone());
    }

    @Test
    void futureGetThrowsExecutionExceptionOnFailure() {
        scheduler = new TaskScheduler(1, 10);
        Task<String> future = scheduler.submit(() -> { throw new RuntimeException("fail"); });
        assertThrows(ExecutionException.class, () -> future.get(3, TimeUnit.SECONDS));
    }

    @Test
    void rejectsTaskAfterShutdown() {
        scheduler = new TaskScheduler(1, 10);
        scheduler.shutdown();
        assertThrows(RejectedExecutionException.class, () -> scheduler.submit(() -> "rejected"));
    }

    @Test
    void shutdownDrainsQueuedTasks() throws Exception {
        scheduler = new TaskScheduler(1, 10);
        AtomicInteger counter = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(3);
        for (int i = 0; i < 3; i++) {
            scheduler.submit(() -> {
                counter.incrementAndGet();
                latch.countDown();
            });
        }
        scheduler.shutdown();
        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertEquals(3, counter.get());
        assertTrue(scheduler.isTerminated());
    }

    @Test
    void priorityTasksExecutedByPriorityOrder() throws Exception {
        scheduler = new TaskScheduler(1, 10);
        CountDownLatch blockLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        StringBuilder sb = new StringBuilder();
        scheduler.submit(() -> {
            try { blockLatch.await(3, TimeUnit.SECONDS); } catch (Exception ignored) {}
            sb.append("H");
            doneLatch.countDown();
        }, Priority.HIGH);
        scheduler.submit(() -> { sb.append("L"); doneLatch.countDown(); }, Priority.LOW);
        blockLatch.countDown();
        assertTrue(doneLatch.await(3, TimeUnit.SECONDS));
        scheduler.shutdown();
        assertEquals("HL", sb.toString());
    }

    @Test
    void awaitTerminationReturnsTrue() throws Exception {
        scheduler = new TaskScheduler(2, 10);
        scheduler.shutdown();
        assertTrue(scheduler.awaitTermination(3, TimeUnit.SECONDS));
    }

    @Test
    void shutdownNowDrainsAndReturnsQueuedTasks() throws Exception {
        CountDownLatch block = new CountDownLatch(1);
        scheduler = new TaskScheduler(1, 10);
        scheduler.submit(() -> {
            try { block.await(3, TimeUnit.SECONDS); } catch (Exception ignored) {}
        });
        Task<?> queued = scheduler.submit(() -> "not-executed");
        List<Task<?>> remaining = scheduler.shutdownNow();
        assertFalse(remaining.isEmpty());
        block.countDown();
    }

    @Test
    void shutdownNowInterruptsIdleWorkers() throws Exception {
        scheduler = new TaskScheduler(2, 10);
        scheduler.shutdownNow();
        assertTrue(scheduler.awaitTermination(2, TimeUnit.SECONDS));
    }
}
