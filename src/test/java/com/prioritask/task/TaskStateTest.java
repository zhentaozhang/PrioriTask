package com.prioritask.task;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskStateTest {

    @Test
    void isTerminal() {
        assertTrue(TaskState.COMPLETED.isTerminal());
        assertTrue(TaskState.FAILED.isTerminal());
        assertTrue(TaskState.CANCELLED.isTerminal());
        assertFalse(TaskState.SUBMITTED.isTerminal());
        assertFalse(TaskState.RUNNING.isTerminal());
    }
}
