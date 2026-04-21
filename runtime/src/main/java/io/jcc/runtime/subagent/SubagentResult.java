package io.jcc.runtime.subagent;

import io.jcc.core.Usage;

public record SubagentResult(String text, Usage usage, TaskStatus status, String taskId) {}
