package com.prioritask.queue;

import com.prioritask.task.Task;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class FifoTaskQueue implements TaskQueue {

    private final LinkedBlockingQueue<Task<?>> queue;

    public FifoTaskQueue(int capacity) {
        this.queue = new LinkedBlockingQueue<>(capacity);
    }

    @Override
    public boolean offer(Task<?> task) {
        return queue.offer(task);
    }

    @Override
    public Task<?> poll() {
        return queue.poll();
    }

    @Override
    public Task<?> poll(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
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
        int drained = 0;
        while (drained < maxElements) {
            Task<?> task = queue.poll();
            if (task == null) break;
            c.add(task);
            drained++;
        }
        return drained;
    }

    @Override
    public List<Task<?>> drainTo() {
        List<Task<?>> drained = new ArrayList<>();
        queue.drainTo(drained);
        return drained;
    }
}
