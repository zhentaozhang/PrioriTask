package com.prioritask.worker;

import java.util.concurrent.atomic.AtomicInteger;

public class WorkerFactory {

    private final String namePrefix;
    private final boolean daemon;
    private final AtomicInteger threadCounter = new AtomicInteger(0);

    private WorkerFactory(String namePrefix, boolean daemon) {
        this.namePrefix = namePrefix;
        this.daemon = daemon;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Thread newThread(Runnable target) {
        String threadName = namePrefix + "-" + threadCounter.incrementAndGet();
        Thread thread = new Thread(target, threadName);
        thread.setDaemon(daemon);
        return thread;
    }

    public static class Builder {
        private String namePrefix = "worker";
        private boolean daemon = false;

        public Builder namePrefix(String prefix) {
            this.namePrefix = prefix;
            return this;
        }

        public Builder daemon(boolean daemon) {
            this.daemon = daemon;
            return this;
        }

        public WorkerFactory build() {
            return new WorkerFactory(namePrefix, daemon);
        }
    }
}
