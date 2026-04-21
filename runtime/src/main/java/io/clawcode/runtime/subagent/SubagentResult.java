package io.clawcode.runtime.subagent;

import io.clawcode.core.Usage;

public record SubagentResult(String text, Usage usage, TaskStatus status, String taskId) {}
