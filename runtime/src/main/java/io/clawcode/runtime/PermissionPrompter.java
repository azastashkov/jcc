package io.clawcode.runtime;

public interface PermissionPrompter {

    PermissionPromptDecision decide(PermissionRequest request);

    PermissionPrompter DENY_ALL = request ->
        new PermissionPromptDecision.Deny("no prompter available");
}
