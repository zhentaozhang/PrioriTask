package com.prioritask.monitor;

import com.prioritask.common.TaskListener;
import com.prioritask.task.Task;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class PoolMetrics implements TaskListener {

    private volatile boolean enabled = true;
    private volatile int sampleDenominator = 1;

    private static final long[] BUCKET_BOUNDARIES = {
        1, 2, 4, 8, 16, 32, 64, 128, 256, 512,
        1000, 2000, 4000, 8000, 16000, 32000, 64000, 128000, 256000, 512000,
        1_000_000, 2_000_000, 4_000_000, 8_000_000, 10_000_000
    };

    private final LongAdder[] buckets;
    private final LongAdder totalSubmitted = new LongAdder();
    private final LongAdder totalCompleted = new LongAdder();
    private final LongAdder totalFailed = new LongAdder();
    private final LongAdder totalSamples = new LongAdder();
    private final LongAdder totalQueueWaitMicros = new LongAdder();
    private final AtomicLong maxQueueWaitMicros = new AtomicLong(0);
    private final ThreadLocal<Long> startTimes = new ThreadLocal<>();

    public PoolMetrics() {
        buckets = new LongAdder[BUCKET_BOUNDARIES.length];
        for (int i = 0; i < buckets.length; i++) {
            buckets[i] = new LongAdder();
        }
    }

    @Override
    public void beforeExecute(Thread thread, Task<?> task) {
        totalSubmitted.increment();
        if (!enabled) return;
        if (sampleDenominator > 1 && ThreadLocalRandom.current().nextInt(sampleDenominator) != 0) {
            startTimes.remove();
            return;
        }
        startTimes.set(System.nanoTime());
        long queueWait = task.startTime() - task.submittedAt();
        if (queueWait > 0) {
            totalQueueWaitMicros.add(queueWait * 1000);
            recordMaxQueueWaitMicros(queueWait * 1000);
        }
    }

    private void recordMaxQueueWaitMicros(long value) {
        while (true) {
            long current = maxQueueWaitMicros.get();
            if (value <= current) break;
            if (maxQueueWaitMicros.compareAndSet(current, value)) break;
        }
    }

    @Override
    public void afterExecute(Task<?> task, Throwable error) {
        if (error != null) {
            totalFailed.increment();
        }
        totalCompleted.increment();
        Long startNanos = startTimes.get();
        if (startNanos != null && enabled) {
            long micros = (System.nanoTime() - startNanos) / 1000;
            record(micros);
        }
        startTimes.remove();
    }

    private void record(long micros) {
        totalSamples.increment();
        for (int i = 0; i < BUCKET_BOUNDARIES.length; i++) {
            if (micros <= BUCKET_BOUNDARIES[i]) {
                buckets[i].increment();
                return;
            }
        }
        buckets[BUCKET_BOUNDARIES.length - 1].increment();
    }

    public double p50() {
        return percentile(50);
    }

    public double p95() {
        return percentile(95);
    }

    public double p99() {
        return percentile(99);
    }

    private double percentile(int p) {
        long total = totalSamples.sum();
        if (total == 0) return 0;
        long target = total * p / 100;
        long cumulative = 0;
        for (int i = 0; i < BUCKET_BOUNDARIES.length; i++) {
            long count = buckets[i].sum();
            cumulative += count;
            if (cumulative >= target) {
                long lower = i == 0 ? 0 : BUCKET_BOUNDARIES[i - 1];
                long upper = BUCKET_BOUNDARIES[i];
                double fraction = (double) (cumulative - target) / count;
                return upper - fraction * (upper - lower);
            }
        }
        return BUCKET_BOUNDARIES[BUCKET_BOUNDARIES.length - 1];
    }

    public long totalSubmitted() {
        return totalSubmitted.sum();
    }

    public long totalCompleted() {
        return totalCompleted.sum();
    }

    public long totalFailed() {
        return totalFailed.sum();
    }

    public double avgQueueWaitMicros() {
        long completed = totalCompleted.sum();
        if (completed == 0) return 0;
        return (double) totalQueueWaitMicros.sum() / completed;
    }

    public long maxQueueWaitMicros() {
        return maxQueueWaitMicros.get();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setSampleRate(int rate) {
        this.sampleDenominator = Math.max(1, rate);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int sampleDenominator() {
        return sampleDenominator;
    }

    @Override
    public String toString() {
        return String.format(
            "PoolMetrics{submitted=%d, completed=%d, failed=%d, p50=%.1f\u00b5s, p95=%.1f\u00b5s, p99=%.1f\u00b5s, avgQueueWait=%.1f\u00b5s, maxQueueWait=%d\u00b5s}",
            totalSubmitted(), totalCompleted(), totalFailed(), p50(), p95(), p99(),
            avgQueueWaitMicros(), maxQueueWaitMicros()
        );
    }
}
