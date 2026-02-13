package com.prioritask.queue;

import com.prioritask.task.Task;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class PriorityTaskQueue implements TaskQueue {

    private final PriorityBlockingQueue<Task<?>> queue;
    private final Semaphore capacity;

    public PriorityTaskQueue(int capacity) {
        this.capacity = new Semaphore(capacity);
        this.queue = new PriorityBlockingQueue<>();
    }

    @Override
    public boolean offer(Task<?> task) {
        if (!capacity.tryAcquire()) {
            return false;
        }
        queue.offer(task);
        return true;
    }

    @Override
    public Task<?> poll() {
        Task<?> task = queue.poll();
        if (task != null) {
            capacity.release();
        }
        return task;
    }

    @Override
    public Task<?> poll(long timeout, TimeUnit unit) throws InterruptedException {
        Task<?> task = queue.poll(timeout, unit);
        if (task != null) {
            capacity.release();
        }
        return task;
    }

    @Override
    public int size() {
        return queue.size();
    }

    @Override
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    @Override
    public int drainTo(Collection<? super Task<?>> c, int maxElements) {
        int n = queue.drainTo(c, maxElements);
        if (n > 0) {
            capacity.release(n);
        }
        return n;
    }

    @Override
    public List<Task<?>> drainTo() {
        List<Task<?>> drained = new ArrayList<>();
        queue.drainTo(drained);
        int n = drained.size();
        if (n > 0) {
            capacity.release(n);
        }
        return drained;
    }
}
