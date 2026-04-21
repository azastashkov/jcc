package io.clawcode.runtime;

import io.clawcode.core.Usage;

public record TurnSummary(String finalText, Usage totalUsage, String stopReason, int turns) {}
