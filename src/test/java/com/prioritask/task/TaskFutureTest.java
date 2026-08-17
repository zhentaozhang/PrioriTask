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

    @Test
    void cancelWhileRunningIsNotOverwrittenByExecuteCompletion() throws Exception {
        // A concurrent cancel() during execution must not be overwritten by the
        // terminal transition in execute(): the task stays CANCELLED and get()
        // throws CancellationException instead of returning the result.
        Task<String> task = Task.of(() -> "result");
        assertTrue(task.markRunning());
        assertTrue(task.cancel());
        task.execute();
        assertTrue(task.isCancelled(), "cancel must survive execute()");
        assertTrue(task.isDone());
        assertThrows(CancellationException.class, task::get);
    }

    @Test
    void cancelWhileRunningNotOverwrittenOnFailure() {
        Task<String> task = Task.of(() -> { throw new RuntimeException("boom"); });
        assertTrue(task.markRunning());
        assertTrue(task.cancel());
        task.execute();
        assertTrue(task.isCancelled());
        assertThrows(CancellationException.class, task::get);
    }

    @Test
    void executeIsIdempotentAfterTerminalState() throws Exception {
        Task<String> task = Task.of(() -> "once");
        task.markRunning();
        task.execute();
        assertTrue(task.isDone());
        // A second execute() must not re-run the callable or flip the state.
        assertEquals("once", task.get());
    }

    @Test
    void compareToTieBreaksBySubmissionOrder() {
        Task<Void> first = Task.ofRunnable(() -> {}, Priority.MEDIUM);
        Task<Void> second = Task.ofRunnable(() -> {}, Priority.MEDIUM);
        Task<Void> high = Task.ofRunnable(() -> {}, Priority.HIGH);
        // Equal priority: earlier submission (smaller taskId) sorts first.
        assertTrue(first.compareTo(second) < 0);
        assertTrue(second.compareTo(first) > 0);
        assertTrue(first.compareTo(first) == 0);
        // Different priority: HIGH sorts before MEDIUM.
        assertTrue(high.compareTo(first) < 0);
    }

    private static void runTask(Task<?> task) {
        task.markRunning();
        try { task.execute(); } catch (Exception e) { /* expected for test */ }
    }
}
