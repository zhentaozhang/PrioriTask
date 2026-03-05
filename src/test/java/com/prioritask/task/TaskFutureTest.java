package com.prioritask.task;

import org.junit.jupiter.api.Test;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import static org.junit.jupiter.api.Assertions.*;

class TaskFutureTest {

    @Test
    void getReturnsResultAfterExecution() throws Exception {
        Task<String> task = Task.of(() -> "hello");
        task.markRunning();
        task.execute();
        assertEquals("hello", task.get());
    }

    @Test
    void getThrowsExecutionExceptionOnFailure() {
        Task<String> task = Task.of(() -> { throw new RuntimeException("boom"); });
        task.markRunning();
        task.execute();
        assertThrows(ExecutionException.class, task::get);
    }

    @Test
    void isDoneAfterCompletion() throws Exception {
        Task<String> task = Task.of(() -> "done");
        runTask(task);
        assertTrue(task.isDone());
    }

    @Test
    void cancelPreventsExecution() {
        Task<String> task = Task.of(() -> "cancelled");
        assertTrue(task.cancel(true));
        assertTrue(task.isCancelled());
    }

    @Test
    void cancelAlreadyDoneReturnsFalse() throws Exception {
        Task<String> task = Task.of(() -> "done");
        runTask(task);
        assertFalse(task.cancel(true));
    }

    @Test
    void getBlocksUntilCompletion() throws Exception {
        Task<String> task = Task.of(() -> "blocking");
        new Thread(() -> {
            try { Thread.sleep(50); task.markRunning(); task.execute(); }
            catch (Exception e) { throw new RuntimeException(e); }
        }).start();
        assertEquals("blocking", task.get());
    }

    @Test
    void getWithTimeoutReturnsResult() throws Exception {
        Task<String> task = Task.of(() -> "timeout-test");
        new Thread(() -> {
            try { Thread.sleep(20); task.markRunning(); task.execute(); }
            catch (Exception e) { throw new RuntimeException(e); }
        }).start();
        assertEquals("timeout-test", task.get(2, TimeUnit.SECONDS));
    }

    @Test
    void getWithTimeoutThrowsOnTimeout() {
        Task<String> task = Task.of(() -> { try { Thread.sleep(5000); } catch (Exception e) {} return "never"; });
        new Thread(() -> { task.markRunning(); task.execute(); }).start();
        assertThrows(TimeoutException.class, () -> task.get(50, TimeUnit.MILLISECONDS));
    }

    @Test
    void cancelledGetThrowsCancellationException() {
        Task<String> task = Task.of(() -> "cancelled");
        task.cancel(true);
        assertThrows(CancellationException.class, task::get);
    }

    private static void runTask(Task<?> task) {
        task.markRunning();
        try { task.execute(); } catch (Exception e) { /* expected for test */ }
    }
}
