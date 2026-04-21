package io.clawcode.core;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ContentBlock.Text.class, name = "text"),
    @JsonSubTypes.Type(value = ContentBlock.ToolUse.class, name = "tool_use"),
    @JsonSubTypes.Type(value = ContentBlock.ToolResult.class, name = "tool_result"),
    @JsonSubTypes.Type(value = ContentBlock.Thinking.class, name = "thinking"),
    @JsonSubTypes.Type(value = ContentBlock.RedactedThinking.class, name = "redacted_thinking")
})
public sealed interface ContentBlock {

    @JsonTypeName("text")
    record Text(String text) implements ContentBlock {}

    @JsonTypeName("tool_use")
    record ToolUse(String id, String name, JsonNode input) implements ContentBlock {}

    @JsonTypeName("tool_result")
    record ToolResult(String toolUseId, List<ToolResultContentBlock> content, boolean isError)
        implements ContentBlock {}

    @JsonTypeName("thinking")
    record Thinking(String thinking, String signature) implements ContentBlock {}

    @JsonTypeName("redacted_thinking")
    record RedactedThinking(JsonNode data) implements ContentBlock {}
}
