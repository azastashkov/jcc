package io.jcc.tools;

import com.fasterxml.jackson.databind.JsonNode;
import io.jcc.api.ToolDefinition;
import io.jcc.core.Result;
import io.jcc.runtime.PermissionMode;
import io.jcc.runtime.ToolContext;
import io.jcc.runtime.ToolError;
import io.jcc.runtime.ToolOutput;
import io.jcc.runtime.subagent.SubagentExecutor;
import io.jcc.runtime.subagent.SubagentResult;
import io.jcc.runtime.subagent.TaskStatus;

public final class AgentTool implements BuiltinTool {

    private static final ToolDefinition SPEC = new ToolDefinition(
        "Agent",
        "Launch a specialized sub-agent task. The sub-agent runs with a restricted tool set based "
            + "on subagent_type (Explore, Plan, Verification, jcc-guide, statusline-setup, or default) "
            + "and returns a concise final result.",
        ToolSchemas.object(
            "description", "string:Short description of the task.!",
            "prompt", "string:The full task prompt for the sub-agent.!",
            "subagent_type", "string:Optional agent type (Explore, Plan, Verification, jcc-guide, statusline-setup).",
            "model", "string:Optional model override for this sub-agent."));

    private final SubagentExecutor executor;

    public AgentTool(SubagentExecutor executor) {
        this.executor = executor;
    }

    @Override
    public ToolDefinition spec() {
        return SPEC;
    }

    @Override
    public PermissionMode requiredMode() {
        return PermissionMode.DANGER_FULL_ACCESS;
    }

    @Override
    public Result<ToolOutput, ToolError> execute(JsonNode input, ToolContext ctx) {
        if (input == null || !input.hasNonNull("description") || !input.hasNonNull("prompt")) {
            return Result.err(ToolError.of("Agent requires 'description' and 'prompt'."));
        }
        String description = input.get("description").asText();
        String prompt = input.get("prompt").asText();
        String subagentType = input.hasNonNull("subagent_type")
            ? input.get("subagent_type").asText()
            : null;
        String modelOverride = input.hasNonNull("model") ? input.get("model").asText() : null;

        SubagentResult result = executor.run(description, prompt, subagentType, modelOverride);
        String annotated = String.format("[subagent task=%s status=%s]%n%s",
            result.taskId(), result.status().wire(), result.text());
        if (result.status() == TaskStatus.COMPLETED) {
            return Result.ok(new ToolOutput.Text(annotated));
        }
        return Result.err(ToolError.of(annotated));
    }
}
