package io.jcc.runtime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PermissionMode {
    READ_ONLY("read-only"),
    WORKSPACE_WRITE("workspace-write"),
    DANGER_FULL_ACCESS("danger-full-access"),
    PROMPT("prompt"),
    ALLOW("allow");

    private final String cliName;

    PermissionMode(String cliName) {
        this.cliName = cliName;
    }

    @JsonValue
    public String cliName() {
        return cliName;
    }

    @JsonCreator
    public static PermissionMode fromCliName(String value) {
        for (PermissionMode mode : values()) {
            if (mode.cliName.equals(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown permission mode: " + value);
    }
}
