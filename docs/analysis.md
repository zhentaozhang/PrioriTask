# Stage 1 — 架构分析与重构计划

> 保存日期: 2026-07-26
> 项目: Custom Thread Pool Task Scheduler → Mini Executor Framework
> 目标: 从"手写线程池 Demo"升级为"体现 Java 并发底层能力的高级开源项目"

---

## 一、项目现状

### 当前结构

```
prioritask/                    (包: com.prioritask)
├── pom.xml                    Maven, Java 17, JUnit 5
├── README.md
├── Main.java                  演示入口
├── ThreadPool.java            核心线程池 — 115 行
├── WorkerThread.java          Worker 线程 — 71 行
├── Task.java                  任务模型 — 58 行
├── TaskQueue.java             队列包装 — 43 行
├── ScheduledTask.java         定时调度 — 49 行
├── ThreadPoolStats.java       统计 — 47 行
└── ThreadPoolTest.java        7 个测试 — 143 行
```

### 架构图

```
ThreadPool
├── TaskQueue (PriorityBlockingQueue wrapper)
│   └── Task (Runnable + priority + submittedAt)
├── WorkerThread × N (extends Thread, poll→execute→record)
├── ThreadPoolStats (4× AtomicLong)
└── ScheduledTask (wraps JDK ScheduledExecutorService)
```

---

## 二、代码质量问题

### [P0] 并发安全 Bug

- [ ] **P0-Q01** `TaskQueue.submit()`: `size() >= capacity` 检查与 `offer()` 非原子 → 多线程并发超出 capacity
- [ ] **P0-Q02** `shutdown()` 期间任务丢失: worker 在 `poll()` 阻塞时收到 `running=false`，返回 null 后退出；若恰好有新任务提交则永久滞留
- [ ] **P0-Q03** `shutdownNow()` 调用 `interrupt()` 打断 `poll()` → worker 直接 break，队列中剩余任务全部丢失

### [P1] 类设计缺陷

- [ ] **P1-Q04** `ThreadPool` 上帝类: 同时管理 workers 生命周期 + 队列 + shutdown 状态 + stats
- [ ] **P1-Q05** `WorkerThread extends Thread`: 与 Thread 强耦合，无法使用 Virtual Threads
- [ ] **P1-Q06** `Task` 只支持 Runnable: 无 Callable/Future/返回值/任务状态/取消
- [ ] **P1-Q07** `ScheduledTask` 依赖 JDK `ScheduledExecutorService`，破坏项目核心价值
- [ ] **P1-Q08** `TaskQueue` 是薄包装: 直接委托 PriorityBlockingQueue，无可扩展性

### [P2] 功能缺失

- [ ] **P2-Q09** 无 Callable/Future 返回值支持
- [ ] **P2-Q10** 无拒绝策略（Abort/Discard/CallerRuns）
- [ ] **P2-Q11** 无 `awaitTermination()`
- [ ] **P2-Q12** 无 ThreadFactory
- [ ] **P2-Q13** 无 Hook 机制（beforeExecute/afterExecute）
- [ ] **P2-Q14** 无 TaskExceptionHandler
- [ ] **P2-Q15** 无 JMH 基准测试
- [ ] **P2-Q16** 无动态扩缩容 / keepalive（可选）

### [P3] 命名与工程化

- [ ] **P3-Q17** package 无模块划分
- [ ] **P3-Q18** `ThreadPoolStats` → `PoolMetrics`; `ScheduledTask` → `TaskScheduler`
- [ ] **P3-Q19** `ThreadPool.submit(Task)` 与 `TaskQueue.submit(Task)` 同名异义
- [ ] **P3-Q20** 缺少 docs/design.md、CHANGELOG.md

---

## 三、执行计划（调整后）

### Phase 1: 核心重构（单 module，package 分层）

> **不做 Maven 多模块**，等代码稳定后再考虑。当前 8 个 java 文件拆 7 个 module 工程量过度。

```
src/main/java/com/prioritask/
├── core/              TaskScheduler, LifecycleState, RejectedExecutionHandler
├── task/              Task<V>, TaskFuture<V>, TaskState, Priority
├── queue/             TaskQueue 接口, PriorityTaskQueue, FifoTaskQueue
├── worker/            Worker, WorkerPool, WorkerFactory
├── scheduler/         TaskScheduler（调度器）, ScheduledTaskHandle
├── monitor/           PoolMetrics
└── common/            TaskExceptionHandler, TaskListener
```

- [ ] **1.1 生命周期管理**: `LifecycleState` 枚举 + `AtomicReference<LifecycleState>`（不做 JDK ctl 复制）
- [ ] **1.2 Worker 重构**: `extends Thread` → `implements Runnable`; `WorkerPool` 统一管理增删
- [ ] **1.3 Task 模型升级**: `Task<V>` 支持 Callable/Runnable 统一, `TaskFuture<V>` 实现 Future 接口
- [ ] **1.4 队列抽象**: `TaskQueue` 接口 + `PriorityTaskQueue`（优先队列）+ `FifoTaskQueue`（FIFO）
- [ ] **1.5 TaskScheduler 重写**: 替换 ThreadPool，分离职责
- [ ] **1.6 修复 P0 竞态**: 容量用 Semaphore 控制; shutdown 先 reject 再 drain; interrupt 不丢任务

### Phase 2: JDK ThreadPoolExecutor 对标能力

> 这部分面试价值最高。

- [ ] **2.1 拒绝策略**: `RejectedExecutionHandler` 接口 + `AbortPolicy` / `DiscardPolicy` / `CallerRunsPolicy`
- [ ] **2.2 ThreadFactory**: 自定义线程命名/daemon/优先级
- [ ] **2.3 awaitTermination**: 超时等待终止
- [ ] **2.4 Hook 机制**: `TaskListener` — `beforeExecute(Thread, Task)` / `afterExecute(Task, Throwable)`
- [ ] **2.5 异常处理**: `TaskExceptionHandler` + `TaskExecutionException` / `RejectedExecutionException`

### Phase 3: 自研调度系统

> **不做时间轮**。DelayQueue + SchedulerThread 已经足够展示调度原理。

- [ ] **3.1 Scheduler 接口**: `schedule(task, delay)` / `scheduleAtFixedRate` / `scheduleWithFixedDelay`
- [ ] **3.2 TimerScheduler**: DelayQueue + 单线程轮询调度器
- [ ] **3.3 ScheduledTaskHandle**: 持有 Future + 取消/状态查询

### Phase 4: 工程化 & Benchmark

- [ ] **4.1 监控指标**: `PoolMetrics` — count / successCount / failedCount / queueDepth /
      执行时间直方图（P50/P95/P99 桶统计）
- [ ] **4.2 测试完善**: 覆盖 基础 / 并发 / 生命周期 / 异常 / 调度 / 压力
- [ ] **4.3 JMH Benchmark**: 提交吞吐量 / shutdown 耗时 / 与 JDK ThreadPoolExecutor 对比
- [ ] **4.4 文档**: docs/design.md / CHANGELOG.md / README 更新

---

## 四、核心设计决策

| 决策 | 方案 | 理由 |
|------|------|------|
| 模块划分 | 单 module + package 分层 | 代码量小，稳定后再考虑多模块 |
| 生命周期 | `AtomicReference<LifecycleState>` | 简单清晰，不复制 JDK ctl |
| Worker | `implements Runnable` + WorkerPool | 解耦 Thread，支持 Virtual Thread |
| Task | `Task<V>` + `TaskFuture<V>` | 面试价值高，贴近 JDK FutureTask |
| 队列 | TaskQueue 接口 + 多种实现 | 可扩展，不绑定单一策略 |
| 调度 | DelayQueue + SchedulerThread | 先简单，时间轮放最后可选 |
| 监控 | 桶统计直方图（P50/P95/P99） | 平均值误导性大，面试加分 |
| 拒绝 | `RejectedExecutionHandler` | 对标 JDK，面试高频 |
| Hook | `TaskListener`（before/after） | 类似 ThreadPoolExecutor 设计 |
| 异常 | `TaskExceptionHandler` | 异步异常统一处理，框架级设计 |

---

## 五、参考对照

| 概念 | 本项目 | JDK 参考 |
|------|--------|----------|
| 线程池 | TaskScheduler | ThreadPoolExecutor |
| Worker | Worker + WorkerPool | Worker (AQS) |
| 任务 | Task\<V\> + TaskFuture\<V\> | FutureTask |
| 队列 | TaskQueue interface | BlockingQueue |
| 调度 | TimerScheduler | ScheduledThreadPoolExecutor |
| 拒绝 | RejectedExecutionHandler | RejectedExecutionHandler |
| Hook | TaskListener | beforeExecute/afterExecute |
| 异常 | TaskExceptionHandler | UncaughtExceptionHandler |
| 状态 | LifecycleState (AtomicReference) | ctl (AtomicInteger runState+workerCount) |

---

## 六、不做的功能（有意识排除）

| 功能 | 原因 |
|------|------|
| Maven 多模块 | 代码量小，过度工程 |
| 时间轮算法 | 实现复杂、展示价值有限，放最后可选 |
| 动态扩缩容 | 面试非核心，复杂度高 |
| 微服务集成 | 偏离"展示 Java 底层并发"定位 |
| 第三方库依赖 | 破坏手写价值 |
