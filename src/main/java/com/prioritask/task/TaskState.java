package com.prioritask.task;

public enum TaskState {
    SUBMITTED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED;

    public static TaskState initial() {
        return SUBMITTED;
    }

    public TaskState transitionTo(TaskState target) {
        boolean valid = switch (this) {
            case SUBMITTED -> target == RUNNING || target == CANCELLED;
            case RUNNING   -> target == COMPLETED || target == FAILED || target == CANCELLED;
            default        -> false;
        };
        if (!valid) {
            throw new IllegalStateException(
                "Invalid task state transition: " + this + " -> " + target);
        }
        return target;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
