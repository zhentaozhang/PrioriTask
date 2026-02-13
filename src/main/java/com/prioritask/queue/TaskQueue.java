package com.prioritask.queue;

import com.prioritask.task.Task;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

public interface TaskQueue {
    boolean offer(Task<?> task);
    Task<?> poll();
    Task<?> poll(long timeout, TimeUnit unit) throws InterruptedException;
    int size();
    boolean isEmpty();
    List<Task<?>> drainTo();

    int drainTo(Collection<? super Task<?>> c, int maxElements);
}
