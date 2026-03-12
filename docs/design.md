# Mini Executor Framework - 设计文档

## 项目定位

从零实现的自研线程池 + 任务调度框架，对标 JDK `ThreadPoolExecutor` + `ScheduledThreadPoolExecutor`，展示 Java 并发底层能力。

## 架构

```
com.prioritask/
├── core/          TaskScheduler, LifecycleState
├── task/          Task<V>, TaskFuture<V>, TaskState, Priority
├── queue/         TaskQueue 接口, PriorityTaskQueue, FifoTaskQueue
├── worker/        Worker, WorkerPool, WorkerFactory
├── scheduler/     TimerScheduler, ScheduledTaskHandle, DelayedTask
├── monitor/       PoolMetrics
└── common/        RejectedExecutionHandler, TaskListener, TaskExceptionHandler,
                   RejectedExecutionException
```

## 核心设计决策

| 概念 | 本项目 | JDK 对标 |
|------|--------|----------|
| 线程池 | TaskScheduler | ThreadPoolExecutor |
| Worker | Worker + WorkerPool | Worker (AQS) |
| 任务模型 | Task\<V\> + TaskFuture\<V\> | FutureTask |
| 队列 | TaskQueue 接口 | BlockingQueue |
| 调度 | TimerScheduler (DelayQueue) | ScheduledThreadPoolExecutor |
| 拒绝策略 | RejectedExecutionHandler | RejectedExecutionHandler |
| Hook | TaskListener (before/afterExecute) | ThreadPoolExecutor 钩子 |
| 异常处理 | TaskExceptionHandler | UncaughtExceptionHandler |
| 生命周期 | LifecycleState (AtomicReference) | ctl (AtomicInteger) |

## 并发安全

- 状态管理: `AtomicReference<LifecycleState>` CAS 切换
- Worker 计数器: `AtomicInteger` for activeCount
- 队列容量: `AtomicInteger` CAS 控制 offer 边界
- Hook 注入: `volatile` 引用，Worker 运行时读取
- 取消传播: `AtomicBoolean` 在 Handle 与 RecurringRunnable 之间共享
- 指标累加: `LongAdder` 高吞吐并发累加

## 调度实现

TimerScheduler 基于 `DelayQueue<DelayedTask>` + 单守护线程轮询:

- **schedule**: 到期提交到 TaskScheduler 执行
- **scheduleAtFixedRate**: 首期时间 + period * iteration
- **scheduleWithFixedDelay**: 当前时间 + delay (Worker 线程上执行，不阻塞调度线程)

## 监控指标

PoolMetrics 通过 TaskListener 注入:

- 直方图桶: 幂次分桶 (1μs ~ 10s)，25 个桶
- P50/P95/P99: 桶内线性插值
- 使用 ThreadLocal 记录开始时间 (nanoTime 精度)

## 排除项（有意识不做）

- Maven 多模块: 代码量小
- 时间轮: DelayQueue 足够展示调度原理
- 动态扩缩容: 面试非核心
- 第三方依赖: 破坏手写价值
