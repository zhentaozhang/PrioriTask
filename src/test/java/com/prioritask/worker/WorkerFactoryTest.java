package com.prioritask.worker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkerFactoryTest {

    @Test
    void factoryCreatesNamedThread() {
        WorkerFactory factory = WorkerFactory.builder()
            .namePrefix("pool-worker")
            .build();
        Thread thread = factory.newThread(() -> {});
        assertTrue(thread.getName().startsWith("pool-worker"));
    }

    @Test
    void factoryCreatesDaemonThread() {
        WorkerFactory factory = WorkerFactory.builder()
            .daemon(true)
            .build();
        Thread thread = factory.newThread(() -> {});
        assertTrue(thread.isDaemon());
    }

    @Test
    void factoryCreatesNonDaemonByDefault() {
        WorkerFactory factory = WorkerFactory.builder().build();
        Thread thread = factory.newThread(() -> {});
        assertFalse(thread.isDaemon());
    }
}
