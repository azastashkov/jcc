package io.jcc.runtime.subagent;

public record TaskRecord(
    String taskId,
    String description,
    String subagentType,
    TaskStatus status,
    long createdAtMs,
    long updatedAtMs,
    String outputSummary,
    Thread runningThread
) {}
