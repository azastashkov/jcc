package io.jcc.cli;

import com.fasterxml.jackson.databind.JsonNode;
import io.jcc.core.Usage;
import io.jcc.runtime.AssistantEventHandler;

public final class RenderingAssistantHandler implements AssistantEventHandler {

    private final StreamingRenderer renderer;
    private Usage runningUsage = Usage.EMPTY;

    public RenderingAssistantHandler(StreamingRenderer renderer) {
        this.renderer = renderer;
    }

    @Override
    public void onTextDelta(String text) {
        renderer.onEvent(new AssistantEvent.TextDelta(text));
    }

    @Override
    public void onThinking(String text) {
        renderer.onEvent(new AssistantEvent.Thinking(text));
    }

    @Override
    public void onToolUseEnd(String id, String name, JsonNode input) {
        renderer.onEvent(new AssistantEvent.ToolUseRequested(id, name,
            input == null ? "{}" : input.toString()));
    }

    @Override
    public void onToolResult(String id, String name, String output, boolean isError) {
        renderer.onEvent(new AssistantEvent.ToolResult(id, name, output, isError));
    }

    @Override
    public void onUsage(Usage usage) {
        runningUsage = runningUsage.plus(usage);
        renderer.onEvent(new AssistantEvent.UsageReport(runningUsage));
    }

    @Override
    public void onTurnFinish(String stopReason, int turns) {
        renderer.onEvent(new AssistantEvent.TurnFinish(stopReason));
    }
}
