package com.prioritask.benchmark;

import com.prioritask.core.TaskScheduler;
import com.prioritask.monitor.PoolMetrics;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

public class Baseline {

    static final int POOL_SIZE = 4;
    static final int QUEUE_CAP = 500000;
    static final int TASK_COUNT = 200000;
    static final int WARMUP = 50000;

    public static void main(String[] args) throws Exception {
        System.out.println("=== Baseline Benchmark ===");
        System.out.println("poolSize=" + POOL_SIZE + " tasks=" + TASK_COUNT + " warmup=" + WARMUP);
        System.out.println("Throughput = tasks completed per second (end-to-end)");
        System.out.println();

        bench("custom_empty",         () -> wrap(() -> runCustom(() -> {})));
        bench("custom_empty_metr",    () -> wrap(() -> runCustomWithMetrics(() -> {})));
        bench("custom_empty_smp10",   () -> wrap(() -> runCustomWithMetrics(() -> {}, 10)));
        bench("custom_empty_smp100",  () -> wrap(() -> runCustomWithMetrics(() -> {}, 100)));
        bench("jdk_empty",            () -> wrap(() -> runJdk(() -> {})));
        System.out.println();
        bench("custom_short_100us",   () -> wrap(() -> runCustom(() -> busySpin(100_000))));
        bench("jdk_short_100us",      () -> wrap(() -> runJdk(() -> busySpin(100_000))));
        System.out.println();
        bench("custom_1ms",           () -> wrap(() -> runCustom(() -> park(1_000_000))));
        bench("jdk_1ms",              () -> wrap(() -> runJdk(() -> park(1_000_000))));
    }

    static void warmup(TaskScheduler s, Runnable task) throws Exception {
        CountDownLatch wl = new CountDownLatch(WARMUP);
        for (int i = 0; i < WARMUP; i++)
            s.submit(() -> { task.run(); wl.countDown(); return null; });
        wl.await(30, TimeUnit.SECONDS);
    }

    static void warmupJdk(ThreadPoolExecutor p, Runnable task) throws Exception {
        CountDownLatch wl = new CountDownLatch(WARMUP);
        for (int i = 0; i < WARMUP; i++)
            p.submit(() -> { task.run(); wl.countDown(); });
        wl.await(30, TimeUnit.SECONDS);
    }

    static BenchResult runCustom(Runnable task) throws Exception {
        TaskScheduler s = new TaskScheduler(POOL_SIZE, QUEUE_CAP);
        warmup(s, task);

        CountDownLatch latch = new CountDownLatch(TASK_COUNT);
        long start = System.nanoTime();
        for (int i = 0; i < TASK_COUNT; i++)
            s.submit(() -> { task.run(); latch.countDown(); return null; });
        latch.await(30, TimeUnit.SECONDS);
        long end = System.nanoTime();
        double secs = (end - start) / 1_000_000_000.0;

        BenchResult r = new BenchResult();
        r.throughput = TASK_COUNT / secs;
        r.elapsed = secs;
        System.out.printf("  %.0f tasks/s  [%.2fs]%n", r.throughput, secs);
        s.shutdown();
        return r;
    }

    static BenchResult runCustomWithMetrics(Runnable task) throws Exception {
        return runCustomWithMetrics(task, 1);
    }

    static BenchResult runCustomWithMetrics(Runnable task, int sampleRate) throws Exception {
        TaskScheduler s = new TaskScheduler(POOL_SIZE, QUEUE_CAP);
        PoolMetrics m = new PoolMetrics();
        m.setSampleRate(sampleRate);
        s.setTaskListener(m);
        warmup(s, task);

        CountDownLatch latch = new CountDownLatch(TASK_COUNT);
        long start = System.nanoTime();
        for (int i = 0; i < TASK_COUNT; i++)
            s.submit(() -> { task.run(); latch.countDown(); return null; });
        latch.await(30, TimeUnit.SECONDS);
        long end = System.nanoTime();
        double secs = (end - start) / 1_000_000_000.0;

        BenchResult r = new BenchResult();
        r.throughput = TASK_COUNT / secs;
        r.elapsed = secs;
        r.p50 = m.p50();
        r.p95 = m.p95();
        r.p99 = m.p99();
        System.out.printf("  %.0f tasks/s  [%.2fs]  p50/p95/p99: %.0f/%.0f/%.0f μs%n",
            r.throughput, secs, m.p50(), m.p95(), m.p99());
        s.shutdown();
        return r;
    }

    static BenchResult runJdk(Runnable task) throws Exception {
        ThreadPoolExecutor p = new ThreadPoolExecutor(
            POOL_SIZE, POOL_SIZE, 1, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(QUEUE_CAP));
        p.allowCoreThreadTimeOut(true);
        warmupJdk(p, task);

        CountDownLatch latch = new CountDownLatch(TASK_COUNT);
        long start = System.nanoTime();
        for (int i = 0; i < TASK_COUNT; i++)
            p.submit(() -> { task.run(); latch.countDown(); });
        latch.await(30, TimeUnit.SECONDS);
        long end = System.nanoTime();
        double secs = (end - start) / 1_000_000_000.0;

        BenchResult r = new BenchResult();
        r.throughput = TASK_COUNT / secs;
        r.elapsed = secs;
        System.out.printf("  %.0f tasks/s  [%.2fs]%n", r.throughput, secs);
        p.shutdownNow();
        return r;
    }

    static void bench(String name, Runnable r) {
        System.out.print(name);
        System.out.flush();
        long t = System.nanoTime();
        r.run();
        System.out.printf("  [elapsed %.2fs]%n", (System.nanoTime() - t) / 1_000_000_000.0);
    }

    interface ThrowingRunnable { void run() throws Exception; }
    static void wrap(ThrowingRunnable r) {
        try { r.run(); } catch (Exception e) { throw new RuntimeException(e); }
    }

    static void busySpin(long nanos) {
        long deadline = System.nanoTime() + nanos;
        while (System.nanoTime() < deadline) Thread.onSpinWait();
    }

    static void park(long nanos) {
        LockSupport.parkNanos(nanos);
    }

    static class BenchResult {
        double throughput;
        double elapsed;
        double p50, p95, p99;
    }
}
