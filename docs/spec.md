# Mini Executor Framework — 规格文档

## 1. 项目概述

从零实现的自研 Java 线程池 + 任务调度框架，对标 JDK `ThreadPoolExecutor` + `ScheduledThreadPoolExecutor`。

### 1.1 定位

- 展示 Java 并发底层能力（CAS / LockSupport / volatile / ThreadLocal / LongAdder）
- 实现 JDK 线程池核心能力的子集，不依赖第三方库
- 可嵌入、可观测、可扩展

### 1.2 命名空间

```
com.prioritask
├── common     — 公共接口与异常
├── core       — 线程池与生命周期
├── task       — 任务模型
├── queue      — 队列抽象与实现
├── worker     — Worker 与线程池管理
├── scheduler  — 定时调度
└── monitor    — 监控指标
```

---

## 2. 功能需求

### FR-1: 任务提交

| ID | 描述 | 优先级 |
|----|------|--------|
| FR-1.1 | 支持 `Runnable` 任务提交 | P0 |
| FR-1.2 | 支持 `Callable<V>` 任务提交并返回结果 | P0 |
| FR-1.3 | 支持任务优先级（HIGH / MEDIUM / LOW） | P0 |
| FR-1.4 | 队列满时触发拒绝策略 | P0 |
| FR-1.5 | shutdown 后提交触发拒绝策略 | P0 |

### FR-2: 任务生命周期

```
SUBMITTED → RUNNING → COMPLETED
                   ↘ → FAILED
SUBMITTED → CANCELLED
RUNNING   → CANCELLED
```

| ID | 描述 | 优先级 |
|----|------|--------|
| FR-2.1 | 状态转换线程安全（AtomicReference + CAS） | P0 |
| FR-2.2 | 任务可取消（从 SUBMITTED 或 RUNNING） | P0 |
| FR-2.3 | 任务完成后可通过 `Future.get()` 获取结果 | P0 |
| FR-2.4 | 任务失败后可通过 `Future.get()` 获取异常 | P0 |

### FR-3: 线程池生命周期

```
RUNNING → SHUTDOWN → TERMINATED
RUNNING → STOP     → TERMINATED
```

| ID | 描述 | 优先级 |
|----|------|--------|
| FR-3.1 | 优雅关闭 - 停止接受新任务，执行完已提交任务 | P0 |
| FR-3.2 | 强制关闭 - interrupt 所有 Worker，返回未完成任务 | P0 |
| FR-3.3 | awaitTermination - 等待线程池终止 | P0 |
| FR-3.4 | 查询状态 - isTerminated / state() | P0 |

### FR-4: 定时调度

| ID | 描述 | 优先级 |
|----|------|--------|
| FR-4.1 | `schedule(task, delay)` - 一次性延迟执行 | P0 |
| FR-4.2 | `scheduleAtFixedRate(task, initialDelay, period)` - 固定频率 | P0 |
| FR-4.3 | `scheduleWithFixedDelay(task, initialDelay, delay)` - 固定延迟 | P0 |
| FR-4.4 | 取消定时任务（取消后续执行） | P0 |
| FR-4.5 | shutdown 停止调度线程 | P0 |

### FR-5: 拒绝策略

| ID | 描述 | 优先级 |
|----|------|--------|
| FR-5.1 | AbortPolicy - 抛出 RejectedExecutionException | P0 |
| FR-5.2 | DiscardPolicy - 静默丢弃 | P0 |
| FR-5.3 | CallerRunsPolicy - 调用者线程执行 | P0 |

### FR-6: Hook 与异常

| ID | 描述 | 优先级 |
|----|------|--------|
| FR-6.1 | beforeExecute(Thread, Task) - 任务执行前回调 | P0 |
| FR-6.2 | afterExecute(Task, Throwable) - 任务执行后回调 | P0 |
| FR-6.3 | TaskExceptionHandler - 异步异常统一回调 | P0 |
| FR-6.4 | RejectedExecutionException - 标准拒绝异常 | P0 |

### FR-7: 监控

| ID | 描述 | 优先级 |
|----|------|--------|
| FR-7.1 | 提交/完成/失败计数 | P0 |
| FR-7.2 | 执行时间直方图（P50 / P95 / P99） | P0 |
| FR-7.3 | 排队时间 | P1 |
| FR-7.4 | Worker 利用率 | P2 |

---

## 3. 非功能性需求

### NFR-1: 线程安全

| ID | 要求 | 实现策略 |
|----|------|---------|
| NFR-1.1 | 所有 public API 线程安全 | AtomicReference / CAS / volatile / LongAdder |
| NFR-1.2 | 无死锁 | 禁止嵌套锁（单个 ReentrantLock 在 PBQ 内部） |
| NFR-1.3 | 内存可见性 | volatile 保证共享变量跨线程可见 |
| NFR-1.4 | 安全发布 | 构造器中完成后 `this` 才暴露 |

### NFR-2: 性能

| ID | 指标 | 目标 | 实测 |
|----|------|------|------|
| NFR-2.1 | 空任务提交吞吐量 | > 1M ops/s (4 workers) | 待 JMH 验证 |
| NFR-2.2 | 10ms 任务吞吐量 | > poolSize × 100 tps | 待 JMH 验证 |
| NFR-2.3 | P99 延迟偏差 | < 任务时长的 20% | 待验证 |
| NFR-2.4 | 定时任务延迟精度 | ± 50ms 以内 | 待验证 |

### NFR-3: 可观测

| ID | 要求 |
|----|------|
| NFR-3.1 | 运行时可附加/分离 TaskListener |
| NFR-3.2 | PoolMetrics 可实时查询 P50/P95/P99 |
| NFR-3.3 | 周期性任务可查询迭代状态 |

### NFR-4: 资源安全

| ID | 要求 |
|----|------|
| NFR-4.1 | Worker 异常死亡后自动重建 |
| NFR-4.2 | 队列容量硬限制，拒绝超出 |
| NFR-4.3 | shutdown 后所有资源释放（无残留线程） |

---

## 4. 模块规格

### 4.1 `core.TaskScheduler`

```java
public class TaskScheduler {
    // 构造
    public TaskScheduler(int poolSize, int queueCapacity);
    public TaskScheduler(int poolSize);  // default queueCapacity = 100

    // 提交
    <V> TaskFuture<V> submit(Callable<V>);
    <V> TaskFuture<V> submit(Callable<V>, Priority);
    TaskFuture<Void> submit(Runnable);
    TaskFuture<Void> submit(Runnable, Priority);

    // 生命周期
    void shutdown();
    List<Task<?>> shutdownNow();
    boolean awaitTermination(long, TimeUnit);
    boolean isTerminated();
    LifecycleState state();

    // 配置注入
    void setRejectedHandler(RejectedExecutionHandler);
    void setTaskListener(TaskListener);
    void setExceptionHandler(TaskExceptionHandler);
}
```

**线程安全**：全部 public 方法线程安全。
**状态契约**：RUNNING → SHUTDOWN 一次转换，不可逆。

### 4.2 `worker.Worker`

```java
// package-private
class Worker implements Runnable {
    Worker(TaskQueue, String, WorkerPool);

    // 退出
    void shutdown();
    int tasksCompleted();
}
```

**线程模型**：每个 Worker 运行在一个线程上，`thread.start()` 启动。
**执行循环**：
1. `queue.poll(500ms)` 等待任务
2. 获取到任务 → `executeTask(task)`
3. 未获取到 → 检查 `running`，继续或退出

**执行链**：
```
pool.getTaskListener().beforeExecute()
task.markRunning()
task.execute()
pool.getExceptionHandler().onError()  // 仅异常时
pool.getTaskListener().afterExecute()
```

### 4.3 `worker.WorkerPool`

```java
public class WorkerPool {
    WorkerPool(TaskQueue, WorkerFactory);

    void start(int count);              // 创建 count 个 Worker 并启动
    void shutdown();                    // 通知所有 Worker 退出
    void shutdownNow();                 // interrupt 所有线程
    boolean awaitTermination(long, TimeUnit);

    void setTaskListener(TaskListener);
    void setExceptionHandler(TaskExceptionHandler);
    TaskListener getTaskListener();
    TaskExceptionHandler getExceptionHandler();
}
```

**线程安全**：listener / exceptionHandler 通过 volatile 发布，Worker 运行时读取最新引用。

### 4.4 `queue.TaskQueue`

```java
public interface TaskQueue {
    boolean offer(Task<?> task);
    Task<?> poll();
    Task<?> poll(long timeout, TimeUnit unit) throws InterruptedException;
    int size();
    boolean isEmpty();
    List<Task<?>> drainTo();
}
```

**实现 — PriorityTaskQueue**：
- 底层 `PriorityBlockingQueue<Task<?>>` + `AtomicInteger count`
- 容量控制通过 `count` CAS 实现（避免 PBQ 的 `remainingCapacity` 方法依赖）

**实现 — FifoTaskQueue**：
- 底层 `LinkedBlockingQueue<Task<?>>`
- 直接委托，无额外 CAS 层

### 4.5 `task.Task`

```java
public class Task<V> implements Comparable<Task<V>> {
    // 工厂方法
    static <V> Task<V> of(Callable<V>);
    static <V> Task<V> of(Callable<V>, Priority);
    static Task<Void> ofRunnable(Runnable);
    static Task<Void> ofRunnable(Runnable, Priority);

    // 生命周期
    TaskState markRunning();
    V execute();
    boolean cancel();

    // 查询
    String taskId();
    Priority priority();
    TaskState state();
    V get();                        // 非阻塞
    Throwable exception();
    long submittedAt();
    long startTime();
    long finishTime();
    boolean isCancelled();
}
```

**状态驱动**：所有状态转换通过 `AtomicReference<TaskState>` CAS 完成。
**`execute()` 语义**：同步执行 `callable.call()`，写入 result / exception。

### 4.6 `task.TaskFuture`

```java
public class TaskFuture<V> implements Future<V> {
    boolean cancel(boolean mayInterruptIfRunning);
    boolean isCancelled();
    boolean isDone();
    V get() throws InterruptedException, ExecutionException;
    V get(long timeout, TimeUnit unit) throws ..., TimeoutException;
    Task<V> task();
}
```

**⚠️ 已知问题**：
- `get()` 非阻塞：任务未完成时抛出 `IllegalStateException`，违反 JDK `Future.get()` 阻塞契约
- `get(timeout)` 忙等待：`while(!isDone()) { Thread.yield(); }` 导致 CPU 空转

### 4.7 `scheduler.TimerScheduler`

```java
public class TimerScheduler {
    TimerScheduler(TaskScheduler executor);  // 构造时自动启动调度线程

    ScheduledTaskHandle schedule(Runnable task, long delay, TimeUnit unit);
    ScheduledTaskHandle scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit);
    ScheduledTaskHandle scheduleWithFixedDelay(Runnable task, long initialDelay, long delay, TimeUnit unit);

    void shutdown();
    boolean isTerminated();
}
```

**调度线程**：单守护线程 `timer-scheduler`，构造时自动启动。
**循环逻辑**：
```
while (RUNNING) {
    DelayedTask delayed = queue.take();  // 阻塞直到下一个到期任务
    if (!delayed.isCancelled())
        executor.submit(delayed.command());
}
```

**`scheduleAtFixedRate` 实现**：
- `FixedRateRunnable` 在 Worker 上执行完毕后，计算 `nextNanos = firstScheduledNanos + periodNanos * iteration`
- 创建新 `DelayedTask` 放入 `DelayQueue`
- 即 re-schedule 发生在 Worker 线程上，不阻塞调度线程

**`scheduleWithFixedDelay` 实现**：
- 类似但 `nextNanos = System.nanoTime() + delayNanos`

### 4.8 `scheduler.ScheduledTaskHandle`

```java
public class ScheduledTaskHandle {
    boolean cancel(boolean mayInterruptIfRunning);
    boolean isDone();
    boolean isCancelled();
}
```

**取消语义**：设置共享 `AtomicBoolean cancelled`，阻止 `FixedRateRunnable` / `FixedDelayRunnable` 下次 re-queue。已入队列的待执行实例不受影响。

### 4.9 `monitor.PoolMetrics`

```java
public class PoolMetrics implements TaskListener {
    long totalSubmitted();
    long totalCompleted();
    long totalFailed();
    double p50();
    double p95();
    double p99();
}
```

**直方图设计**：幂次分桶（1μs ~ 10s），25 个桶，`LongAdder` 高并发累加。
**百分位计算**：累积桶计数扫描 + 线性插值。
**精度边界**：最小桶 1μs，最大桶 10s，超过 10s 的归入最大桶。

### 4.10 `common` 接口

```java
@FunctionalInterface
interface RejectedExecutionHandler {
    void rejected(Task<?> task, TaskScheduler scheduler);
}

interface TaskListener {
    default void beforeExecute(Thread thread, Task<?> task) {}
    default void afterExecute(Task<?> task, Throwable error) {}
}

@FunctionalInterface
interface TaskExceptionHandler {
    void onError(Task<?> task, Throwable error);
}

class RejectedExecutionException extends RuntimeException {
    RejectedExecutionException(String message);
    RejectedExecutionException(String message, Throwable cause);
}
```

---

## 5. 生命周期规格

### 5.1 线程池状态机

```
                    shutdown()                    所有 Worker 退出
RUNNING ──────────────────────→ SHUTDOWN ─────────────────────→ TERMINATED
  │                                                                ↑
  └── shutdownNow()                                                │
          │                                                        │
          └──→ STOP ───────────────────────────────────────────────┘
                     interrupt + drainTo + awaitTermination
```

| 状态 | 接受新任务 | 处理队列任务 | Worker 状态 |
|------|-----------|-------------|-------------|
| RUNNING | ✅ | ✅ | 运行中 |
| SHUTDOWN | ❌（触发拒绝） | ✅ | 运行中 → 退出 |
| STOP | ❌ | ❌（drainTo 返回） | interrupt + 退出 |
| TERMINATED | ❌ | ❌ | 全部停止 |

### 5.2 Scheduler 状态机

```
                     shutdown()
RUNNING ──────────────────────────────→ TERMINATED
          interrupt schedulerThread
```

**注意**：TimerScheduler 无 SHUTDOWN 中间状态，`shutdown()` 直接中断调度线程 → TERMINATED。

### 5.3 定时任务迭代状态

```
handle = scheduleAtFixedRate(task, 0, 1s)
  ↓
DelayedTask@1 (due=now) → Worker 执行 FixedRateRunnable
  ↓                          ↓ delegate.run()
  ↓                          ↓ queue.put(DelayedTask@2, due=now+1s)
  ↓                          ↓ return (Worker 空闲)
  ↓
  ...
  ↓
handle.cancel(true)
  ↓
AtomicBoolean cancelled = true
  ↓
FixedRateRunnable.run() → cancelled.get() == true → return (no re-queue)
```

---

## 6. 线程安全保证

| 组件 | 机制 | 字段 |
|------|------|------|
| TaskScheduler.state | `AtomicReference<LifecycleState>` | `state` |
| TaskScheduler.rejectedHandler | `volatile` | `rejectedHandler` |
| WorkerPool.listener | `volatile` | `listener` |
| WorkerPool.exceptionHandler | `volatile` | `exceptionHandler` |
| WorkerPool.activeCount | `AtomicInteger` | `activeCount` |
| Worker.running | `volatile` | `running` |
| Worker.tasksCompleted | `AtomicInteger` | `tasksCompleted` |
| Task.state | `AtomicReference<TaskState>` | `state` |
| Task.result | `volatile` | `result` |
| Task.exception | `volatile` | `exception` |
| PriorityTaskQueue.count | `AtomicInteger` | `count` |
| PoolMetrics.buckets[] | `LongAdder` | `buckets` |
| PoolMetrics.startTimes | `ThreadLocal` | `startTimes` |
| TimerScheduler.state | `AtomicReference<LifecycleState>` | `state` |
| FixedRateRunnable.cancelled | `AtomicBoolean` (共享引用) | `cancelled` |

---

## 7. 错误处理

### 7.1 拒绝场景

| 场景 | 行为 |
|------|------|
| RUNNING 但队列满 | `rejectedHandler.rejected()` |
| 非 RUNNING 状态下提交 | `rejectedHandler.rejected()` |
| default handler | 抛出 `RejectedExecutionException` |

### 7.2 任务异常

| 场景 | 行为 |
|------|------|
| `Callable.call()` 抛出异常 | Task 状态 → FAILED，`exception` 字段写入，`afterExecute` 回调传入 |
| `Throwable`（含 Error） | 同上（`execute()` catch 了 `Throwable`） |
| `afterExecute` 自身抛异常 | 向上传播到 Worker 线程（Worker.run 无 catch，线程可能死亡 ⚠️） |

### 7.3 中断

| 场景 | 行为 |
|------|------|
| Worker 在 `poll()` 阻塞时被 interrupt | catch InterruptedException → 检查 isEmpty → 继续或退出 |
| `shutdownNow()` | `Thread.interrupt()` + `drainTo()` |
| 定时间隔中 interrupt | `queue.take()` 抛出 InterruptedException → scheduler 线程退出 |

---

## 8. 性能模型

### 8.1 流水线

```
提交者线程: submit → PBQ.offer (ReentrantLock 竞争)
                                   ↓
Worker 线程:   PBQ.take (ReentrantLock 竞争) → execute → 返回 poll
```

**竞争点**：`PriorityBlockingQueue` 内部 `ReentrantLock` 在 `offer` / `take` 之间竞争。

### 8.2 延迟组成

```
排队延迟: submittedAt → startTime  (从 offer 到 Worker 开始执行)
执行延迟: startTime → finishTime   (callable.call() 实际执行时长)
```

### 8.3 调度延迟

```
TimerScheduler 精度:
  delayed.take() 耗尽精度     = 约 1ms（DelayQueue 基于 Condition.awaitNanos）
+ Worker 池排队延迟           = 取决于当前排队任务数
+ 任务前置处理（listener等）    = 约 1-5μs
────────────────────────────────────
总延迟: 1ms + 排队延迟 + <10μs
```

---

## 9. 未来规格扩展

### 9.1 已确认缺口（待修复）

| ID | 描述 | 优先级 |
|----|------|--------|
| SP-1 | `Future.get()` 改为阻塞通知机制（CountDownLatch / LockSupport） | P0 |
| SP-2 | `PriorityTaskQueue.poll()` 修复 count 负值 bug | P0 |
| SP-3 | Worker 异常死亡后自动重建 | P1 |
| SP-4 | 空闲 Worker keepAlive 超时退出 | P1 |
| SP-5 | 排队时间指标加入 PoolMetrics | P1 |

### 9.2 可选扩展

| ID | 描述 | 复杂度 | 面试价值 |
|----|------|--------|---------|
| SP-O1 | corePoolSize / maxPoolSize 动态扩缩容 | 中 | 高 |
| SP-O2 | 任务超时后 interrupt 中止 | 中 | 中 |
| SP-O3 | 分层时间轮替换 DelayQueue | 高 | 高 |
| SP-O4 | JMX MBean 注册 | 低 | 中 |
| SP-O5 | 任务重试（失败自动重新入队） | 低 | 低 |

---

## 10. 参考对照

| 概念 | 本项目 | JDK 参考 |
|------|--------|----------|
| 线程池 | TaskScheduler | ThreadPoolExecutor |
| Worker | Worker + WorkerPool | Worker (AQS) |
| 任务模型 | Task\<V\> + TaskFuture\<V\> | FutureTask |
| 队列 | TaskQueue 接口 | BlockingQueue |
| 调度 | TimerScheduler (DelayQueue) | ScheduledThreadPoolExecutor |
| 拒绝 | RejectedExecutionHandler | RejectedExecutionHandler |
| Hook | TaskListener | beforeExecute/afterExecute |
| 异常 | TaskExceptionHandler | UncaughtExceptionHandler |
| 状态 | LifecycleState (AtomicReference) | ctl (AtomicInteger) |
