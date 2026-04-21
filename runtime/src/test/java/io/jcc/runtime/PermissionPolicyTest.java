package io.jcc.runtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionPolicyTest {

    private final PermissionPrompter denyAll = request ->
        new PermissionPromptDecision.Deny("prompter denied");

    @Test
    void readOnlyToolAllowedInAnyMode() {
        PermissionPolicy policy = new PermissionPolicy(PermissionMode.READ_ONLY)
            .withToolRequirement("read_file", PermissionMode.READ_ONLY);
        assertThat(policy.evaluate("read_file", "{}", denyAll))
            .isInstanceOf(PermissionOutcome.Allow.class);
    }

    @Test
    void workspaceWriteBlockedInReadOnly() {
        PermissionPolicy policy = new PermissionPolicy(PermissionMode.READ_ONLY)
            .withToolRequirement("write_file", PermissionMode.WORKSPACE_WRITE);
        PermissionOutcome outcome = policy.evaluate("write_file", "{}", denyAll);
        assertThat(outcome).isInstanceOf(PermissionOutcome.Deny.class);
        assertThat(((PermissionOutcome.Deny) outcome).reason()).contains("workspace-write");
    }

    @Test
    void workspaceWriteAllowedInWorkspaceMode() {
        PermissionPolicy policy = new PermissionPolicy(PermissionMode.WORKSPACE_WRITE)
            .withToolRequirement("write_file", PermissionMode.WORKSPACE_WRITE);
        assertThat(policy.evaluate("write_file", "{}", denyAll))
            .isInstanceOf(PermissionOutcome.Allow.class);
    }

    @Test
    void dangerFullAccessAllowsEverything() {
        PermissionPolicy policy = new PermissionPolicy(PermissionMode.DANGER_FULL_ACCESS)
            .withToolRequirement("bash", PermissionMode.WORKSPACE_WRITE);
        assertThat(policy.evaluate("bash", "{}", denyAll))
            .isInstanceOf(PermissionOutcome.Allow.class);
    }

    @Test
    void promptModeConsultsThePrompter() {
        PermissionPolicy policy = new PermissionPolicy(PermissionMode.PROMPT)
            .withToolRequirement("write_file", PermissionMode.WORKSPACE_WRITE);

        PermissionOutcome denied = policy.evaluate("write_file", "{}", denyAll);
        assertThat(denied).isInstanceOf(PermissionOutcome.Deny.class);
        assertThat(((PermissionOutcome.Deny) denied).reason()).isEqualTo("prompter denied");

        PermissionPrompter allowAll = request -> PermissionPromptDecision.Allow.INSTANCE;
        assertThat(policy.evaluate("write_file", "{}", allowAll))
            .isInstanceOf(PermissionOutcome.Allow.class);
    }
}
