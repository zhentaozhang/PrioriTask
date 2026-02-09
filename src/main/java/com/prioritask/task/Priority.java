package com.prioritask.task;

public enum Priority {
    LOW(1),
    MEDIUM(2),
    HIGH(3);

    private final int level;

    Priority(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }

    public static Priority defaultPriority() {
        return MEDIUM;
    }

    public int compareToPriority(Priority other) {
        return Integer.compare(other.level, this.level);
    }
}
