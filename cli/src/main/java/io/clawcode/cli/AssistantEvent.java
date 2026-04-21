package io.clawcode.cli;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.clawcode.core.Usage;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = AssistantEvent.TextDelta.class, name = "text_delta"),
    @JsonSubTypes.Type(value = AssistantEvent.Thinking.class, name = "thinking"),
    @JsonSubTypes.Type(value = AssistantEvent.ToolUseRequested.class, name = "tool_use_requested"),
    @JsonSubTypes.Type(value = AssistantEvent.UsageReport.class, name = "usage_report"),
    @JsonSubTypes.Type(value = AssistantEvent.TurnFinish.class, name = "turn_finish")
})
public sealed interface AssistantEvent {

    @JsonTypeName("text_delta")
    record TextDelta(String text) implements AssistantEvent {}

    @JsonTypeName("thinking")
    record Thinking(String text) implements AssistantEvent {}

    @JsonTypeName("tool_use_requested")
    record ToolUseRequested(String id, String name, String inputJson) implements AssistantEvent {}

    @JsonTypeName("usage_report")
    record UsageReport(Usage usage) implements AssistantEvent {}

    @JsonTypeName("turn_finish")
    record TurnFinish(String stopReason) implements AssistantEvent {}
}
