package io.clawcode.runtime;

import com.fasterxml.jackson.databind.JsonNode;

public sealed interface ToolOutput {

    record Text(String text) implements ToolOutput {}

    record Structured(JsonNode value) implements ToolOutput {}

    default String asText() {
        return switch (this) {
            case Text t -> t.text;
            case Structured s -> s.value.toString();
        };
    }
}
