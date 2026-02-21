package com.prioritask.core;

public enum LifecycleState {
    RUNNING,
    SHUTDOWN,
    STOP,
    TERMINATED;

    public static LifecycleState initial() {
        return RUNNING;
    }

    public LifecycleState transitionTo(LifecycleState target) {
        boolean valid = switch (this) {
            case RUNNING   -> target == SHUTDOWN || target == STOP;
            case SHUTDOWN  -> target == TERMINATED;
            case STOP      -> target == TERMINATED;
            case TERMINATED -> false;
        };
        if (!valid) {
            throw new IllegalStateException(
                "Invalid state transition: " + this + " -> " + target);
        }
        return target;
    }

    public boolean isAtLeast(LifecycleState other) {
        return this.ordinal() >= other.ordinal();
    }

    public boolean isRunningOrShutdown() {
        return this == RUNNING || this == SHUTDOWN;
    }
}
