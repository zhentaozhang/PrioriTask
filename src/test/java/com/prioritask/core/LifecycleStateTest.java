package com.prioritask.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LifecycleStateTest {

    @Test
    void initialStateIsRunning() {
        assertEquals(LifecycleState.RUNNING, LifecycleState.initial());
    }

    @Test
    void runningToShutdownIsValid() {
        assertEquals(LifecycleState.SHUTDOWN, LifecycleState.RUNNING.transitionTo(LifecycleState.SHUTDOWN));
    }

    @Test
    void runningToStopIsValid() {
        assertEquals(LifecycleState.STOP, LifecycleState.RUNNING.transitionTo(LifecycleState.STOP));
    }

    @Test
    void shutdownToTerminatedIsValid() {
        assertEquals(LifecycleState.TERMINATED, LifecycleState.SHUTDOWN.transitionTo(LifecycleState.TERMINATED));
    }

    @Test
    void stopToTerminatedIsValid() {
        assertEquals(LifecycleState.TERMINATED, LifecycleState.STOP.transitionTo(LifecycleState.TERMINATED));
    }

    @Test
    void runningToTerminatedThrows() {
        assertThrows(IllegalStateException.class,
            () -> LifecycleState.RUNNING.transitionTo(LifecycleState.TERMINATED));
    }

    @Test
    void shutdownToRunningThrows() {
        assertThrows(IllegalStateException.class,
            () -> LifecycleState.SHUTDOWN.transitionTo(LifecycleState.RUNNING));
    }

    @Test
    void terminatedToAnyThrows() {
        assertThrows(IllegalStateException.class,
            () -> LifecycleState.TERMINATED.transitionTo(LifecycleState.RUNNING));
        assertThrows(IllegalStateException.class,
            () -> LifecycleState.TERMINATED.transitionTo(LifecycleState.SHUTDOWN));
    }

    @Test
    void isAtLeastRunning() {
        assertTrue(LifecycleState.RUNNING.isAtLeast(LifecycleState.RUNNING));
        assertFalse(LifecycleState.RUNNING.isAtLeast(LifecycleState.SHUTDOWN));
    }

    @Test
    void isAtLeastShutdown() {
        assertTrue(LifecycleState.SHUTDOWN.isAtLeast(LifecycleState.RUNNING));
        assertTrue(LifecycleState.SHUTDOWN.isAtLeast(LifecycleState.SHUTDOWN));
        assertFalse(LifecycleState.SHUTDOWN.isAtLeast(LifecycleState.STOP));
    }

    @Test
    void shutdownIsRunningOrShutdown() {
        assertTrue(LifecycleState.SHUTDOWN.isRunningOrShutdown());
        assertTrue(LifecycleState.RUNNING.isRunningOrShutdown());
        assertFalse(LifecycleState.STOP.isRunningOrShutdown());
        assertFalse(LifecycleState.TERMINATED.isRunningOrShutdown());
    }
}
