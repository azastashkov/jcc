package io.clawcode.core;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class Concurrency implements AutoCloseable {

    private final ExecutorService virtualThreads;
    private final ScheduledExecutorService scheduled;

    public Concurrency() {
        this.virtualThreads = Executors.newVirtualThreadPerTaskExecutor();
        this.scheduled = Executors.newScheduledThreadPool(2, namedDaemon("claw-scheduler"));
    }

    public ExecutorService virtualThreads() {
        return virtualThreads;
    }

    public ScheduledExecutorService scheduled() {
        return scheduled;
    }

    @Override
    public void close() {
        scheduled.shutdownNow();
        virtualThreads.shutdown();
    }

    private static ThreadFactory namedDaemon(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread t = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
