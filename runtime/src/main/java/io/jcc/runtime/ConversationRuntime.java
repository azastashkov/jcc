package io.jcc.runtime;

import io.jcc.api.InputMessage;
import io.jcc.api.MessageRequest;
import io.jcc.api.ProviderClient;
import io.jcc.core.ContentBlock;
import io.jcc.core.Result;
import io.jcc.core.ToolResultContentBlock;
import io.jcc.core.Usage;

import java.util.ArrayList;
import java.util.List;

public final class ConversationRuntime {

    private static final int MAX_TURNS = 25;

    private final ProviderClient provider;
    private final ToolExecutor toolExecutor;
    private final PermissionPolicy permissions;
    private final PermissionPrompter prompter;
    private final ToolContext toolContext;
    private final String model;
    private final int maxTokens;
    private final String system;

    private final List<InputMessage> history = new ArrayList<>();

    public ConversationRuntime(
        ProviderClient provider,
        ToolExecutor toolExecutor,
        PermissionPolicy permissions,
        PermissionPrompter prompter,
        ToolContext toolContext,
        String model,
        int maxTokens,
        String system
    ) {
        this.provider = provider;
        this.toolExecutor = toolExecutor;
        this.permissions = permissions;
        this.prompter = prompter;
        this.toolContext = toolContext;
        this.model = model;
        this.maxTokens = maxTokens;
        this.system = system;
    }

    public List<InputMessage> history() {
        return List.copyOf(history);
    }

    public void addHistory(InputMessage message) {
        history.add(message);
    }

    public void clearHistory() {
        history.clear();
    }

    public TurnSummary runTurn(String userPrompt, AssistantEventHandler handler) {
        history.add(InputMessage.userText(userPrompt));

        Usage aggregate = Usage.EMPTY;
        String finalText = "";
        String lastStopReason = null;

        for (int turn = 1; turn <= MAX_TURNS; turn++) {
            MessageRequest request = MessageRequest.builder()
                .model(model)
                .maxTokens(maxTokens)
                .messages(history)
                .system(system)
                .tools(toolExecutor.toolDefinitions())
                .build();

            TurnAssembler assembler = new TurnAssembler(handler);
            provider.stream(request, assembler);

            List<ContentBlock> assistantBlocks = assembler.assembledBlocks();
            history.add(new InputMessage("assistant", assistantBlocks));

            aggregate = aggregate.plus(assembler.usage());
            finalText = assembler.collectedText();
            lastStopReason = assembler.stopReason();

            List<ContentBlock.ToolUse> toolUses = assembler.toolUses();
            if (toolUses.isEmpty()) {
                handler.onTurnFinish(lastStopReason, turn);
                return new TurnSummary(finalText, aggregate, lastStopReason, turn);
            }

            List<ContentBlock> toolResults = new ArrayList<>();
            for (ContentBlock.ToolUse use : toolUses) {
                toolResults.add(runToolCall(use, handler));
            }
            history.add(new InputMessage("user", toolResults));
        }

        handler.onTurnFinish("max_turns_reached", MAX_TURNS);
        return new TurnSummary(finalText, aggregate, "max_turns_reached", MAX_TURNS);
    }

    private ContentBlock runToolCall(ContentBlock.ToolUse use, AssistantEventHandler handler) {
        String inputSummary = use.input() == null ? "" : use.input().toString();
        PermissionOutcome outcome = permissions.evaluate(use.name(), inputSummary, prompter);
        if (outcome instanceof PermissionOutcome.Deny deny) {
            handler.onToolResult(use.id(), use.name(), deny.reason(), true);
            return denyResult(use.id(), deny.reason());
        }

        Result<ToolOutput, ToolError> result =
            toolExecutor.execute(use.name(), use.input(), toolContext);

        return switch (result) {
            case Result.Ok<ToolOutput, ToolError> ok -> {
                String text = ok.value().asText();
                handler.onToolResult(use.id(), use.name(), text, false);
                yield new ContentBlock.ToolResult(
                    use.id(),
                    List.of(new ToolResultContentBlock.Text(text)),
                    false);
            }
            case Result.Err<ToolOutput, ToolError> err -> {
                String text = err.error().message();
                handler.onToolResult(use.id(), use.name(), text, true);
                yield new ContentBlock.ToolResult(
                    use.id(),
                    List.of(new ToolResultContentBlock.Text(text)),
                    true);
            }
        };
    }

    private static ContentBlock.ToolResult denyResult(String id, String reason) {
        return new ContentBlock.ToolResult(
            id,
            List.of(new ToolResultContentBlock.Text("Permission denied: " + reason)),
            true);
    }
}
