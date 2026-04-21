package io.jcc.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jcc.api.ContentBlockDelta;
import io.jcc.api.StreamEvent;
import io.jcc.api.StreamEventHandler;
import io.jcc.core.ContentBlock;
import io.jcc.core.JsonMapper;
import io.jcc.core.Usage;

import java.util.ArrayList;
import java.util.List;

final class TurnAssembler implements StreamEventHandler {

    private final AssistantEventHandler sink;
    private final ObjectMapper mapper;

    private final List<ContentBlock> blocks = new ArrayList<>();
    private StringBuilder currentText;
    private StringBuilder currentToolInputJson;
    private String currentToolId;
    private String currentToolName;

    private Usage usage = Usage.EMPTY;
    private String stopReason;

    TurnAssembler(AssistantEventHandler sink) {
        this(sink, JsonMapper.shared());
    }

    TurnAssembler(AssistantEventHandler sink, ObjectMapper mapper) {
        this.sink = sink;
        this.mapper = mapper;
    }

    @Override
    public void onEvent(StreamEvent event) {
        switch (event) {
            case StreamEvent.MessageStart start -> {
                if (start.message().usage() != null) {
                    usage = start.message().usage();
                }
            }
            case StreamEvent.ContentBlockStart start -> onBlockStart(start.contentBlock());
            case StreamEvent.ContentBlockDeltaEvent delta -> onDelta(delta.delta());
            case StreamEvent.ContentBlockStop ignored -> finalizeCurrentBlock();
            case StreamEvent.MessageDeltaEvent md -> {
                if (md.usage() != null) {
                    usage = usage.plus(md.usage());
                }
                if (md.delta() != null && md.delta().stopReason() != null) {
                    stopReason = md.delta().stopReason();
                }
            }
            case StreamEvent.MessageStop ignored -> {
                finalizeCurrentBlock();
                sink.onUsage(usage);
            }
            case StreamEvent.Ping ignored -> {}
            case StreamEvent.ErrorEvent err ->
                throw new IllegalStateException("Stream error: "
                    + err.error().type() + " - " + err.error().message());
        }
    }

    private void onBlockStart(ContentBlock block) {
        switch (block) {
            case ContentBlock.Text t -> {
                currentText = new StringBuilder(t.text() == null ? "" : t.text());
            }
            case ContentBlock.ToolUse use -> {
                currentToolId = use.id();
                currentToolName = use.name();
                currentToolInputJson = new StringBuilder();
                sink.onToolUseStart(use.id(), use.name());
            }
            case ContentBlock.Thinking t -> {
                currentText = new StringBuilder(t.thinking() == null ? "" : t.thinking());
            }
            default -> {}
        }
    }

    private void onDelta(ContentBlockDelta delta) {
        switch (delta) {
            case ContentBlockDelta.TextDelta td -> {
                if (currentText != null) {
                    currentText.append(td.text());
                }
                sink.onTextDelta(td.text());
            }
            case ContentBlockDelta.InputJsonDelta jd -> {
                if (currentToolInputJson != null) {
                    currentToolInputJson.append(jd.partialJson());
                }
            }
            case ContentBlockDelta.ThinkingDelta th -> {
                if (currentText != null) {
                    currentText.append(th.thinking());
                }
                sink.onThinking(th.thinking());
            }
            case ContentBlockDelta.SignatureDelta ignored -> {}
        }
    }

    private void finalizeCurrentBlock() {
        if (currentToolId != null) {
            JsonNode input = parseToolInput(currentToolInputJson.toString());
            blocks.add(new ContentBlock.ToolUse(currentToolId, currentToolName, input));
            sink.onToolUseEnd(currentToolId, currentToolName, input);
            currentToolId = null;
            currentToolName = null;
            currentToolInputJson = null;
        } else if (currentText != null) {
            blocks.add(new ContentBlock.Text(currentText.toString()));
            currentText = null;
        }
    }

    private JsonNode parseToolInput(String raw) {
        if (raw == null || raw.isBlank()) {
            return mapper.createObjectNode();
        }
        try {
            return mapper.readTree(raw);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse tool_use input: " + raw, e);
        }
    }

    List<ContentBlock> assembledBlocks() {
        return List.copyOf(blocks);
    }

    List<ContentBlock.ToolUse> toolUses() {
        List<ContentBlock.ToolUse> uses = new ArrayList<>();
        for (ContentBlock b : blocks) {
            if (b instanceof ContentBlock.ToolUse use) {
                uses.add(use);
            }
        }
        return uses;
    }

    String collectedText() {
        StringBuilder sb = new StringBuilder();
        for (ContentBlock b : blocks) {
            if (b instanceof ContentBlock.Text t) {
                sb.append(t.text());
            }
        }
        return sb.toString();
    }

    Usage usage() {
        return usage;
    }

    String stopReason() {
        return stopReason;
    }
}
