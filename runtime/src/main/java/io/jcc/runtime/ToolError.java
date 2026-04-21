package io.jcc.runtime;

public record ToolError(String message) {

    public static ToolError of(String message) {
        return new ToolError(message);
    }
}
