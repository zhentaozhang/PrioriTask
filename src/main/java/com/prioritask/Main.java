package com.prioritask;

import com.prioritask.core.TaskScheduler;
import com.prioritask.scheduler.TimerScheduler;
import java.util.concurrent.TimeUnit;

public class Main {

    public static void main(String[] args) throws InterruptedException {

        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║   Mini Executor Framework           ║");
        System.out.println("╚══════════════════════════════════════╝\n");

        System.out.println("--- Demo 1: Basic Task Submission ---");
        TaskScheduler pool = new TaskScheduler(3, 50);

        for (int i = 1; i <= 6; i++) {
            final int taskNum = i;
            pool.submit(() -> {
                System.out.printf("  [Task %d] Running on %s%n", taskNum, Thread.currentThread().getName());
                sleep(200);
                return null;
            });
        }

        Thread.sleep(2000);

        System.out.println("\n--- Demo 2: Priority Task Ordering ---");
        TaskScheduler priorityPool = new TaskScheduler(1, 20);

        priorityPool.submit(() -> { sleep(100); return null; }, com.prioritask.task.Priority.LOW);
        priorityPool.submit(() -> { sleep(100); return null; }, com.prioritask.task.Priority.LOW);
        priorityPool.submit(() -> { sleep(100); return null; }, com.prioritask.task.Priority.MEDIUM);
        priorityPool.submit(() -> { sleep(100); return null; }, com.prioritask.task.Priority.HIGH);
        priorityPool.submit(() -> { sleep(100); return null; }, com.prioritask.task.Priority.HIGH);

        Thread.sleep(1500);
        priorityPool.shutdown();

        System.out.println("\n--- Demo 3: Scheduled Tasks ---");
        TaskScheduler scheduledPool = new TaskScheduler(2);
        TimerScheduler timer = new TimerScheduler(scheduledPool);

        timer.schedule(() ->
            System.out.println("  [Delayed-Report] Running 1 second after scheduling!"),
            1, TimeUnit.SECONDS
        );

        var heartbeat = timer.scheduleAtFixedRate(() ->
            System.out.println("  [Heartbeat] Ping! " + System.currentTimeMillis()),
            0, 500, TimeUnit.MILLISECONDS
        );

        Thread.sleep(2500);
        heartbeat.cancel(true);
        timer.shutdown();
        scheduledPool.shutdown();

        System.out.println("\n--- Demo 4: Stress Test (50 tasks, 4 workers) ---");
        TaskScheduler stressPool = new TaskScheduler(4, 100);

        long startTime = System.currentTimeMillis();
        for (int i = 1; i <= 50; i++) {
            final int num = i;
            com.prioritask.task.Priority priority = num % 3 == 0 ? com.prioritask.task.Priority.HIGH
                : num % 2 == 0 ? com.prioritask.task.Priority.MEDIUM
                : com.prioritask.task.Priority.LOW;
            stressPool.submit(() -> { sleep(50); return null; }, priority);
        }

        stressPool.shutdown();
        long elapsed = System.currentTimeMillis() - startTime;
        System.out.printf("Stress test completed in %dms%n", elapsed);

        pool.shutdown();

        System.out.println("\u2705 All demos complete!");
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
