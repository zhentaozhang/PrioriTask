package com.prioritask.task;

import org.junit.jupiter.api.Test;
import java.util.concurrent.Callable;
import static org.junit.jupiter.api.Assertions.*;

class TaskTest {

    @Test
    void createTaskWithCallable() {
        Task<String> task = Task.of(() -> "hello");
        assertEquals(TaskState.SUBMITTED, task.state());
        assertNotNull(task.taskId());
        assertTrue(task.submittedAt() > 0);
    }

    @Test
    void createTaskWithRunnable() {
        Task<Void> task = Task.ofRunnable(() -> {});
        assertEquals(TaskState.SUBMITTED, task.state());
    }

    @Test
    void createTaskWithCustomPriority() {
        Task<String> task = Task.of(() -> "hi", Priority.HIGH);
        assertEquals(Priority.HIGH, task.priority());
    }

    @Test
    void taskIdIsUnique() {
        Task<Void> t1 = Task.ofRunnable(() -> {});
        Task<Void> t2 = Task.ofRunnable(() -> {});
        assertNotEquals(t1.taskId(), t2.taskId());
    }

    @Test
    void executeCallableReturnsResult() throws Exception {
        Task<String> task = Task.of(() -> "result");
        task.markRunning();
        String result = task.execute();
        assertEquals("result", result);
        assertEquals(TaskState.COMPLETED, task.state());
        assertTrue(task.finishTime() >= task.startTime());
    }

    @Test
    void executeRunnableMarksCompleted() throws Exception {
        Task<Void> task = Task.ofRunnable(() -> {});
        task.markRunning();
        task.execute();
        assertEquals(TaskState.COMPLETED, task.state());
    }

    @Test
    void executeFailedTaskRecordsException() {
        Task<String> task = Task.of(() -> { throw new RuntimeException("fail"); });
        task.markRunning();
        task.execute();
        assertEquals(TaskState.FAILED, task.state());
        assertNotNull(task.exception());
        assertEquals("fail", task.exception().getMessage());
    }

    @Test
    void markRunningTransitionsState() {
        Task<String> task = Task.of(() -> "ok");
        task.markRunning();
        assertEquals(TaskState.RUNNING, task.state());
        assertTrue(task.startTime() > 0);
    }

    @Test
    void cancelSubmittedTask() {
        Task<String> task = Task.of(() -> "never run");
        assertTrue(task.cancel());
        assertEquals(TaskState.CANCELLED, task.state());
    }

    @Test
    void cancelRunningTask() {
        Task<String> task = Task.of(() -> "running");
        task.markRunning();
        assertTrue(task.cancel());
    }

    @Test
    void cancelCompletedTaskReturnsFalse() throws Exception {
        Task<String> task = Task.of(() -> "done");
        task.markRunning();
        task.execute();
        assertFalse(task.cancel());
    }

    @Test
    void priorityOrdering() {
        Task<Void> high = Task.ofRunnable(() -> {}, Priority.HIGH);
        Task<Void> low  = Task.ofRunnable(() -> {}, Priority.LOW);
        assertTrue(high.compareTo(low) < 0);
    }

    @Test
    void resultNowReturnsResultAfterExecute() throws Exception {
        Task<String> task = Task.of(() -> "value");
        task.markRunning();
        task.execute();
        assertEquals("value", task.resultNow());
    }

    @Test
    void resultNowReturnsNullForRunnable() throws Exception {
        Task<Void> task = Task.ofRunnable(() -> {});
        task.markRunning();
        task.execute();
        assertNull(task.resultNow());
    }
}
