package io.jcc.runtime.subagent;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public final class TaskRegistry {

    private final Map<String, TaskRecord> tasks = new LinkedHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicLong nextId = new AtomicLong(1);

    public TaskRecord createTask(String description, String subagentType) {
        lock.lock();
        try {
            long now = System.currentTimeMillis();
            String id = "task-" + now + "-" + nextId.getAndIncrement();
            TaskRecord record = new TaskRecord(id, description, subagentType, TaskStatus.CREATED, now, now, null, null);
            tasks.put(id, record);
            return record;
        } finally {
            lock.unlock();
        }
    }

    public void transition(String taskId, TaskStatus status, Thread thread, String outputSummary) {
        lock.lock();
        try {
            TaskRecord current = tasks.get(taskId);
            if (current == null) return;
            tasks.put(taskId, new TaskRecord(
                current.taskId(), current.description(), current.subagentType(), status,
                current.createdAtMs(), System.currentTimeMillis(),
                outputSummary == null ? current.outputSummary() : outputSummary,
                thread == null ? current.runningThread() : thread));
        } finally {
            lock.unlock();
        }
    }

    public Optional<TaskRecord> get(String taskId) {
        lock.lock();
        try {
            return Optional.ofNullable(tasks.get(taskId));
        } finally {
            lock.unlock();
        }
    }

    public Collection<TaskRecord> all() {
        lock.lock();
        try {
            return java.util.List.copyOf(tasks.values());
        } finally {
            lock.unlock();
        }
    }

    public boolean stop(String taskId) {
        lock.lock();
        try {
            TaskRecord rec = tasks.get(taskId);
            if (rec == null) return false;
            if (rec.runningThread() != null) {
                rec.runningThread().interrupt();
            }
            transition(taskId, TaskStatus.STOPPED, null, "stopped by user");
            return true;
        } finally {
            lock.unlock();
        }
    }
}
