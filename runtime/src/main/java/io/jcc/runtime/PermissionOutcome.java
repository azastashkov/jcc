package io.jcc.runtime;

public sealed interface PermissionOutcome {

    record Allow() implements PermissionOutcome {
        public static final Allow INSTANCE = new Allow();
    }

    record Deny(String reason) implements PermissionOutcome {}
}
