package com.prioritask.task;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskStateTest {

    @Test
    void initialStateIsSubmitted() {
        assertEquals(TaskState.SUBMITTED, TaskState.initial());
    }

    @Test
    void submittedToRunning() {
        assertEquals(TaskState.RUNNING, TaskState.SUBMITTED.transitionTo(TaskState.RUNNING));
    }

    @Test
    void runningToCompleted() {
        assertEquals(TaskState.COMPLETED, TaskState.RUNNING.transitionTo(TaskState.COMPLETED));
    }

    @Test
    void runningToFailed() {
        assertEquals(TaskState.FAILED, TaskState.RUNNING.transitionTo(TaskState.FAILED));
    }

    @Test
    void submittedToCancelled() {
        assertEquals(TaskState.CANCELLED, TaskState.SUBMITTED.transitionTo(TaskState.CANCELLED));
    }

    @Test
    void runningToCancelled() {
        assertEquals(TaskState.CANCELLED, TaskState.RUNNING.transitionTo(TaskState.CANCELLED));
    }

    @Test
    void completedToRunningThrows() {
        assertThrows(IllegalStateException.class,
            () -> TaskState.COMPLETED.transitionTo(TaskState.RUNNING));
    }

    @Test
    void isTerminal() {
        assertTrue(TaskState.COMPLETED.isTerminal());
        assertTrue(TaskState.FAILED.isTerminal());
        assertTrue(TaskState.CANCELLED.isTerminal());
        assertFalse(TaskState.SUBMITTED.isTerminal());
        assertFalse(TaskState.RUNNING.isTerminal());
    }
}
