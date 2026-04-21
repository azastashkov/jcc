package io.clawcode.runtime;

public sealed interface PermissionPromptDecision {

    record Allow() implements PermissionPromptDecision {
        public static final Allow INSTANCE = new Allow();
    }

    record Deny(String reason) implements PermissionPromptDecision {}
}
