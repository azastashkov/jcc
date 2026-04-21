package io.jcc.runtime.subagent;

import io.jcc.api.ProviderClient;
import io.jcc.runtime.AssistantEventHandler;
import io.jcc.runtime.ConversationRuntime;
import io.jcc.runtime.FilteringToolExecutor;
import io.jcc.runtime.PermissionPolicy;
import io.jcc.runtime.PermissionPrompter;
import io.jcc.runtime.ToolContext;
import io.jcc.runtime.ToolExecutor;
import io.jcc.runtime.TurnSummary;

import java.util.Set;

public final class SubagentExecutor {

    private final ProviderClient provider;
    private final ToolExecutor parentTools;
    private final PermissionPolicy permissions;
    private final PermissionPrompter prompter;
    private final ToolContext toolCtx;
    private final String defaultModel;
    private final int maxTokens;
    private final TaskRegistry taskRegistry;

    public SubagentExecutor(
        ProviderClient provider,
        ToolExecutor parentTools,
        PermissionPolicy permissions,
        PermissionPrompter prompter,
        ToolContext toolCtx,
        String defaultModel,
        int maxTokens,
        TaskRegistry taskRegistry
    ) {
        this.provider = provider;
        this.parentTools = parentTools;
        this.permissions = permissions;
        this.prompter = prompter;
        this.toolCtx = toolCtx;
        this.defaultModel = defaultModel;
        this.maxTokens = maxTokens;
        this.taskRegistry = taskRegistry;
    }

    public SubagentResult run(String description, String prompt, String subagentType, String modelOverride) {
        String normalizedType = SubagentToolAllowlist.normalizeType(subagentType);
        Set<String> allowed = SubagentToolAllowlist.allowedTools(normalizedType);
        ToolExecutor filteredTools = new FilteringToolExecutor(parentTools, allowed);

        String systemPrompt = buildSystemPrompt(normalizedType);
        String model = (modelOverride == null || modelOverride.isBlank()) ? defaultModel : modelOverride;

        TaskRecord record = taskRegistry.createTask(description, normalizedType);
        taskRegistry.transition(record.taskId(), TaskStatus.RUNNING, Thread.currentThread(), null);

        ConversationRuntime child = new ConversationRuntime(
            provider, filteredTools, permissions, prompter, toolCtx, model, maxTokens, systemPrompt);

        try {
            TurnSummary summary = child.runTurn(prompt, AssistantEventHandler.NOOP);
            taskRegistry.transition(record.taskId(), TaskStatus.COMPLETED, null, summary.finalText());
            return new SubagentResult(summary.finalText(), summary.totalUsage(),
                TaskStatus.COMPLETED, record.taskId());
        } catch (RuntimeException e) {
            String message = "sub-agent failed: " + e.getMessage();
            taskRegistry.transition(record.taskId(), TaskStatus.FAILED, null, message);
            return new SubagentResult(message, io.jcc.core.Usage.EMPTY,
                TaskStatus.FAILED, record.taskId());
        }
    }

    private static String buildSystemPrompt(String subagentType) {
        String typeLabel = subagentType.isEmpty() ? "(default)" : subagentType;
        return "You are a background sub-agent of type `" + typeLabel + "`. "
            + "Work only on the delegated task, use only the tools available to you, "
            + "do not ask the user questions, and finish with a concise result.";
    }
}
