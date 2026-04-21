package io.jcc.runtime;

public record PermissionRequest(
    String toolName,
    String inputSummary,
    PermissionMode currentMode,
    PermissionMode requiredMode,
    String reason
) {}
