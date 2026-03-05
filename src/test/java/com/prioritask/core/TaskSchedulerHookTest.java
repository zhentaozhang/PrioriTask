package com.prioritask.core;

import com.prioritask.common.RejectedExecutionHandler;
import com.prioritask.common.TaskExceptionHandler;
import com.prioritask.common.TaskListener;
import com.prioritask.task.Task;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class TaskSchedulerHookTest {

    @Test
    void listenerBeforeExecuteIsCalled() throws Exception {
        TaskScheduler scheduler = new TaskScheduler(1, 10);
        AtomicReference<String> captured = new AtomicReference<>();
        scheduler.setTaskListener(new TaskListener() {
            @Override
            public void beforeExecute(Thread thread, Task<?> task) {
                captured.set(thread.getName());
            }
        });
        CountDownLatch latch = new CountDownLatch(1);
        scheduler.submit((Runnable) latch::countDown);
        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertNotNull(captured.get());
        scheduler.shutdown();
    }

    @Test
    void listenerAfterExecuteIsCalledOnSuccess() throws Exception {
        TaskScheduler scheduler = new TaskScheduler(1, 10);
        AtomicInteger afterCount = new AtomicInteger(0);
        scheduler.setTaskListener(new TaskListener() {
            @Override
            public void afterExecute(Task<?> task, Throwable error) {
                assertNull(error);
                afterCount.incrementAndGet();
            }
        });
        CountDownLatch latch = new CountDownLatch(1);
        scheduler.submit((Runnable) latch::countDown);
        assertTrue(latch.await(3, TimeUnit.SECONDS));
        scheduler.shutdown();
        assertEquals(1, afterCount.get());
    }

    @Test
    void exceptionHandlerCalledOnTaskFailure() throws Exception {
        TaskScheduler scheduler = new TaskScheduler(1, 10);
        AtomicReference<Throwable> captured = new AtomicReference<>();
        scheduler.setExceptionHandler((task, error) -> captured.set(error));
        CountDownLatch latch = new CountDownLatch(1);
        scheduler.submit(() -> { latch.countDown(); throw new RuntimeException("custom-error"); });
        assertTrue(latch.await(3, TimeUnit.SECONDS));
        scheduler.shutdown();
        assertNotNull(captured.get());
        assertEquals("custom-error", captured.get().getMessage());
    }

    @Test
    void customRejectedHandlerCalledOnFullQueue() {
        TaskScheduler scheduler = new TaskScheduler(1, 1);
        AtomicInteger rejectCount = new AtomicInteger(0);
        scheduler.setRejectedHandler((task, s) -> rejectCount.incrementAndGet());
        CountDownLatch block = new CountDownLatch(1);
        scheduler.submit(() -> { try { block.await(3, TimeUnit.SECONDS); } catch (Exception ignored) {} });
        scheduler.submit(() -> "fits");
        scheduler.submit(() -> "rejected1");
        scheduler.submit(() -> "rejected2");
        block.countDown();
        scheduler.shutdown();
        assertTrue(rejectCount.get() >= 1);
    }
}
