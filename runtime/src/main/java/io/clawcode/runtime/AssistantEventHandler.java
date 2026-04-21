package io.clawcode.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import io.clawcode.core.Usage;

public interface AssistantEventHandler {

    default void onTextDelta(String text) {}

    default void onThinking(String text) {}

    default void onToolUseStart(String id, String name) {}

    default void onToolUseEnd(String id, String name, JsonNode input) {}

    default void onToolResult(String id, String name, String output, boolean isError) {}

    default void onUsage(Usage usage) {}

    default void onTurnFinish(String stopReason, int turns) {}

    AssistantEventHandler NOOP = new AssistantEventHandler() {};
}
