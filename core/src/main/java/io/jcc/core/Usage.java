package io.jcc.core;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public record Usage(
    int inputTokens,
    int cacheCreationInputTokens,
    int cacheReadInputTokens,
    int outputTokens
) {
    public static final Usage EMPTY = new Usage(0, 0, 0, 0);

    public int totalTokens() {
        return inputTokens + outputTokens + cacheCreationInputTokens + cacheReadInputTokens;
    }

    public Usage plus(Usage other) {
        return new Usage(
            inputTokens + other.inputTokens,
            cacheCreationInputTokens + other.cacheCreationInputTokens,
            cacheReadInputTokens + other.cacheReadInputTokens,
            outputTokens + other.outputTokens
        );
    }
}
