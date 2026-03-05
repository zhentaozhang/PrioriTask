package com.prioritask.monitor;

import com.prioritask.core.TaskScheduler;
import com.prioritask.task.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class PoolMetricsTest {

    private TaskScheduler scheduler;
    private PoolMetrics metrics;

    @BeforeEach
    void setUp() {
        scheduler = new TaskScheduler(2, 20);
        metrics = new PoolMetrics();
        scheduler.setTaskListener(metrics);
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdown();
    }

    @Test
    void recordsSubmissionAndCompletionCounts() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(3);
        for (int i = 0; i < 3; i++) {
            scheduler.submit(() -> { latch.countDown(); return null; });
        }
        assertTrue(latch.await(3, TimeUnit.SECONDS));
        Thread.sleep(100);
        assertEquals(3, metrics.totalSubmitted());
        assertEquals(3, metrics.totalCompleted());
        assertEquals(0, metrics.totalFailed());
    }

    @Test
    void recordsFailedTasks() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        for (int i = 0; i < 2; i++) {
            scheduler.submit(() -> { latch.countDown(); throw new RuntimeException("fail"); });
        }
        assertTrue(latch.await(3, TimeUnit.SECONDS));
        Thread.sleep(100);
        assertEquals(2, metrics.totalFailed());
    }

    @Test
    void percentilesIncreaseWithTaskDuration() throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            scheduler.submit(this::sleep50ms);
        }
        scheduler.shutdown();
        scheduler.awaitTermination(10, TimeUnit.SECONDS);
        assertTrue(metrics.p50() > 0, "p50 should be > 0 for 50ms tasks");
        assertTrue(metrics.p99() >= metrics.p50(), "p99 should be >= p50");
    }

    @Test
    void percentilesZeroWhenNoTasks() {
        PoolMetrics empty = new PoolMetrics();
        assertEquals(0, empty.p50());
        assertEquals(0, empty.p95());
        assertEquals(0, empty.p99());
    }

    @Test
    void toStringContainsKeyMetrics() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        scheduler.submit(() -> { latch.countDown(); return null; });
        assertTrue(latch.await(3, TimeUnit.SECONDS));
        Thread.sleep(100);
        String str = metrics.toString();
        assertTrue(str.contains("submitted=1"));
        assertTrue(str.contains("completed=1"));
        assertTrue(str.contains("p50="));
    }

    @Test
    void queueWaitMetricsRecorded() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        scheduler.submit(() -> { latch.countDown(); return null; });
        assertTrue(latch.await(3, TimeUnit.SECONDS));
        Thread.sleep(100);
        assertTrue(metrics.avgQueueWaitMicros() >= 0);
        assertTrue(metrics.maxQueueWaitMicros() >= 0);
        String str = metrics.toString();
        assertTrue(str.contains("avgQueueWait"));
        assertTrue(str.contains("maxQueueWait"));
    }

    private Void sleep50ms() {
        try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return null;
    }
}
