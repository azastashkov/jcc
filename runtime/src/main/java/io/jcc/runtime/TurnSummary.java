package io.jcc.runtime;

import io.jcc.core.Usage;

public record TurnSummary(String finalText, Usage totalUsage, String stopReason, int turns) {}
