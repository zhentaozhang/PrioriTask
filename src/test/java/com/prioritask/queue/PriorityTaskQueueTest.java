package com.prioritask.queue;

import com.prioritask.task.Priority;
import com.prioritask.task.Task;
import org.junit.jupiter.api.Test;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class PriorityTaskQueueTest {

    @Test
    void highPriorityComesBeforeLow() {
        TaskQueue queue = new PriorityTaskQueue(10);
        Task<Void> low  = Task.ofRunnable(() -> {}, Priority.LOW);
        Task<Void> high = Task.ofRunnable(() -> {}, Priority.HIGH);
        queue.offer(low);
        queue.offer(high);
        assertSame(high, queue.poll());
        assertSame(low, queue.poll());
    }

    @Test
    void fifoOrderForSamePriority() {
        TaskQueue queue = new PriorityTaskQueue(10);
        Task<Void> first  = Task.ofRunnable(() -> {});
        Task<Void> second = Task.ofRunnable(() -> {});
        queue.offer(first);
        queue.offer(second);
        assertSame(first, queue.poll());
        assertSame(second, queue.poll());
    }

    @Test
    void pollReturnsNullWhenEmpty() {
        TaskQueue queue = new PriorityTaskQueue(10);
        assertNull(queue.poll());
    }

    @Test
    void pollWithTimeoutReturnsNullOnEmpty() throws InterruptedException {
        TaskQueue queue = new PriorityTaskQueue(10);
        assertNull(queue.poll(100, TimeUnit.MILLISECONDS));
    }

    @Test
    void offerRejectsWhenFull() {
        TaskQueue queue = new PriorityTaskQueue(2);
        assertTrue(queue.offer(Task.ofRunnable(() -> {})));
        assertTrue(queue.offer(Task.ofRunnable(() -> {})));
        assertFalse(queue.offer(Task.ofRunnable(() -> {})));
    }

    @Test
    void sizeReflectsEnqueuedTasks() {
        TaskQueue queue = new PriorityTaskQueue(10);
        assertEquals(0, queue.size());
        queue.offer(Task.ofRunnable(() -> {}));
        assertEquals(1, queue.size());
    }

    @Test
    void isEmptyWhenNoTasks() {
        TaskQueue queue = new PriorityTaskQueue(10);
        assertTrue(queue.isEmpty());
        queue.offer(Task.ofRunnable(() -> {}));
        assertFalse(queue.isEmpty());
    }
}
