package io.jcc.runtime;

import java.util.HashMap;
import java.util.Map;

public final class PermissionPolicy {

    private final PermissionMode activeMode;
    private final Map<String, PermissionMode> toolRequirements;

    public PermissionPolicy(PermissionMode activeMode) {
        this(activeMode, Map.of());
    }

    public PermissionPolicy(PermissionMode activeMode, Map<String, PermissionMode> toolRequirements) {
        this.activeMode = activeMode;
        this.toolRequirements = Map.copyOf(toolRequirements);
    }

    public PermissionMode activeMode() {
        return activeMode;
    }

    public PermissionPolicy withToolRequirement(String toolName, PermissionMode required) {
        Map<String, PermissionMode> merged = new HashMap<>(toolRequirements);
        merged.put(toolName, required);
        return new PermissionPolicy(activeMode, merged);
    }

    public PermissionMode requiredMode(String toolName) {
        return toolRequirements.getOrDefault(toolName, PermissionMode.WORKSPACE_WRITE);
    }

    public PermissionOutcome evaluate(String toolName, String inputSummary, PermissionPrompter prompter) {
        PermissionMode required = requiredMode(toolName);

        if (activeMode == PermissionMode.ALLOW || activeMode == PermissionMode.DANGER_FULL_ACCESS) {
            return PermissionOutcome.Allow.INSTANCE;
        }

        if (activeMode == PermissionMode.PROMPT) {
            PermissionPromptDecision decision = prompter.decide(new PermissionRequest(
                toolName, inputSummary, activeMode, required, null));
            return switch (decision) {
                case PermissionPromptDecision.Allow ignored -> PermissionOutcome.Allow.INSTANCE;
                case PermissionPromptDecision.Deny deny -> new PermissionOutcome.Deny(deny.reason());
            };
        }

        if (required == PermissionMode.READ_ONLY) {
            return PermissionOutcome.Allow.INSTANCE;
        }
        if (required == PermissionMode.WORKSPACE_WRITE && activeMode == PermissionMode.WORKSPACE_WRITE) {
            return PermissionOutcome.Allow.INSTANCE;
        }
        return new PermissionOutcome.Deny(
            "tool '" + toolName + "' requires " + required.cliName()
                + " mode, but current mode is " + activeMode.cliName());
    }
}
