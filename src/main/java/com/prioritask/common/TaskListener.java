package com.prioritask.common;

import com.prioritask.task.Task;

public interface TaskListener {
    default void beforeExecute(Thread thread, Task<?> task) {}
    default void afterExecute(Task<?> task, Throwable error) {}
}
