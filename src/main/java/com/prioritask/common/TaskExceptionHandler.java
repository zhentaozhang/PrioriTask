package com.prioritask.common;

import com.prioritask.task.Task;

@FunctionalInterface
public interface TaskExceptionHandler {
    void onError(Task<?> task, Throwable error);
}
