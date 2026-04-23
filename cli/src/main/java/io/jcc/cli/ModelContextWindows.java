package io.jcc.cli;

import java.util.Map;
import java.util.OptionalInt;

public final class ModelContextWindows {

    private static final Map<String, Integer> WINDOWS = Map.ofEntries(
        // Claude — current generation
        Map.entry("claude-opus-4-7", 1_000_000),
        Map.entry("claude-sonnet-4-6",  200_000),
        Map.entry("claude-haiku-4-5",   200_000),
        // Claude — previous generations commonly seen
        Map.entry("claude-3-7-sonnet",            200_000),
        Map.entry("claude-3-5-sonnet-20241022",   200_000),
        Map.entry("claude-3-5-sonnet-20240620",   200_000),
        Map.entry("claude-3-5-haiku-20241022",    200_000),
        Map.entry("claude-3-opus-20240229",       200_000),
        Map.entry("claude-3-sonnet-20240229",     200_000),
        Map.entry("claude-3-haiku-20240307",      200_000),
        // OpenAI / OpenAI-compat
        Map.entry("gpt-4o",         128_000),
        Map.entry("gpt-4o-mini",    128_000),
        Map.entry("gpt-4-turbo",    128_000),
        Map.entry("gpt-4",            8_192),
        Map.entry("gpt-3.5-turbo",   16_385),
        Map.entry("o1",             200_000),
        Map.entry("o1-mini",        128_000),
        Map.entry("o3-mini",        200_000)
    );

    private ModelContextWindows() {}

    public static OptionalInt get(String model) {
        if (model == null || model.isBlank()) return OptionalInt.empty();
        Integer w = WINDOWS.get(model);
        return w == null ? OptionalInt.empty() : OptionalInt.of(w);
    }
}
