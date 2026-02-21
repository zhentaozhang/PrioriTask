package com.prioritask.common;

import com.prioritask.core.TaskScheduler;
import com.prioritask.task.Task;

@FunctionalInterface
public interface RejectedExecutionHandler {
    void rejected(Task<?> task, TaskScheduler scheduler);
}
