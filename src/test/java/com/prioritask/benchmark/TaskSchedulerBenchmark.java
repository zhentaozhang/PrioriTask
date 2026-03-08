package com.prioritask.benchmark;

import com.prioritask.core.TaskScheduler;
import com.prioritask.monitor.PoolMetrics;
import org.openjdk.jmh.annotations.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 2, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class TaskSchedulerBenchmark {

    private TaskScheduler customPool;
    private TaskScheduler customPoolMetrics;
    private ThreadPoolExecutor jdkPool;
    private final AtomicInteger counter = new AtomicInteger();

    @Param({"4"})
    private int poolSize;

    @Setup
    public void setup() {
        customPool = new TaskScheduler(poolSize, 1_000_000_000);
        customPoolMetrics = new TaskScheduler(poolSize, 1_000_000_000);
        customPoolMetrics.setTaskListener(new PoolMetrics());
        jdkPool = new ThreadPoolExecutor(
            poolSize, poolSize,
            1, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1_000_000_000)
        );
        jdkPool.allowCoreThreadTimeOut(true);
    }

    @TearDown
    public void tearDown() {
        customPool.shutdown();
        customPoolMetrics.shutdown();
        jdkPool.shutdownNow();
    }

    @Benchmark
    public void custom_empty() {
        customPool.submit((Runnable) counter::incrementAndGet);
    }

    @Benchmark
    public void custom_empty_metrics() {
        customPoolMetrics.submit((Runnable) counter::incrementAndGet);
    }

    @Benchmark
    public void jdk_empty() {
        jdkPool.submit((Runnable) counter::incrementAndGet);
    }

    @Benchmark
    public int baseline_counter() {
        return counter.incrementAndGet();
    }
}
