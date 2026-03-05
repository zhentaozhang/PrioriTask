package com.prioritask.scheduler;

import com.prioritask.core.TaskScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class TimerSchedulerTest {

    private TaskScheduler executor;
    private TimerScheduler timer;

    @BeforeEach
    void setUp() {
        executor = new TaskScheduler(2, 10);
        timer = new TimerScheduler(executor);
    }

    @AfterEach
    void tearDown() {
        timer.shutdown();
        executor.shutdown();
    }

    @Test
    void scheduleExecutesTaskAfterDelay() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        timer.schedule(latch::countDown, 10, TimeUnit.MILLISECONDS);
        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }

    @Test
    void scheduleAtFixedRateExecutesMultipleTimes() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(3);
        ScheduledTaskHandle handle = timer.scheduleAtFixedRate(
            latch::countDown, 5, 20, TimeUnit.MILLISECONDS);
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        handle.cancel(true);
    }

    @Test
    void scheduleWithFixedDelayExecutesMultipleTimes() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(3);
        ScheduledTaskHandle handle = timer.scheduleWithFixedDelay(
            latch::countDown, 5, 20, TimeUnit.MILLISECONDS);
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        handle.cancel(true);
    }

    @Test
    void cancelPreventsExecution() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        ScheduledTaskHandle handle = timer.schedule(latch::countDown, 100, TimeUnit.MILLISECONDS);
        handle.cancel(true);
        assertFalse(latch.await(300, TimeUnit.MILLISECONDS));
    }

    @Test
    void cancelStopsRecurringTask() throws InterruptedException {
        AtomicInteger count = new AtomicInteger(0);
        CountDownLatch started = new CountDownLatch(1);
        ScheduledTaskHandle handle = timer.scheduleAtFixedRate(() -> {
            count.incrementAndGet();
            started.countDown();
        }, 5, 10, TimeUnit.MILLISECONDS);
        assertTrue(started.await(1, TimeUnit.SECONDS));
        Thread.sleep(60);
        handle.cancel(true);
        int afterCancel = count.get();
        Thread.sleep(100);
        assertTrue(count.get() <= afterCancel + 1,
            "count increased after cancel: before=" + afterCancel + " after=" + count.get());
    }

    @Test
    void multipleScheduledTasksRunIndependently() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        timer.schedule(latch::countDown, 10, TimeUnit.MILLISECONDS);
        timer.schedule(latch::countDown, 10, TimeUnit.MILLISECONDS);
        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }

    @Test
    void cancelledHandleIsCancelled() {
        ScheduledTaskHandle handle = timer.schedule(() -> {}, 10, TimeUnit.SECONDS);
        handle.cancel(true);
        assertTrue(handle.isCancelled());
    }
}
