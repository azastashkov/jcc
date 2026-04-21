package io.clawcode.cli;

import io.clawcode.api.ContentBlockDelta;
import io.clawcode.api.StreamEvent;
import io.clawcode.api.StreamEventHandler;
import io.clawcode.core.ContentBlock;
import io.clawcode.core.Usage;

public final class StreamEventBridge implements StreamEventHandler {

    private final StreamingRenderer renderer;
    private Usage runningUsage = Usage.EMPTY;

    public StreamEventBridge(StreamingRenderer renderer) {
        this.renderer = renderer;
    }

    @Override
    public void onEvent(StreamEvent event) {
        switch (event) {
            case StreamEvent.MessageStart start -> {
                Usage inbound = start.message().usage();
                if (inbound != null) {
                    runningUsage = inbound;
                }
            }
            case StreamEvent.ContentBlockStart start -> {
                if (start.contentBlock() instanceof ContentBlock.ToolUse use) {
                    renderer.onEvent(new AssistantEvent.ToolUseRequested(use.id(), use.name(), ""));
                }
            }
            case StreamEvent.ContentBlockDeltaEvent delta -> {
                ContentBlockDelta d = delta.delta();
                if (d instanceof ContentBlockDelta.TextDelta td) {
                    renderer.onEvent(new AssistantEvent.TextDelta(td.text()));
                } else if (d instanceof ContentBlockDelta.ThinkingDelta th) {
                    renderer.onEvent(new AssistantEvent.Thinking(th.thinking()));
                }
            }
            case StreamEvent.ContentBlockStop ignored -> {
            }
            case StreamEvent.MessageDeltaEvent md -> {
                if (md.usage() != null) {
                    runningUsage = runningUsage.plus(md.usage());
                }
            }
            case StreamEvent.MessageStop ignored -> {
                renderer.onEvent(new AssistantEvent.UsageReport(runningUsage));
                renderer.onEvent(new AssistantEvent.TurnFinish("end_turn"));
            }
            case StreamEvent.Ping ignored -> {
            }
            case StreamEvent.ErrorEvent err -> {
                throw new IllegalStateException(
                    "Stream error: " + err.error().type() + " - " + err.error().message());
            }
        }
    }
}
