package io.jcc.cli;

import java.util.Map;

public final class ModelAliases {

    public static final String DEFAULT_MODEL = "claude-opus-4-7";

    private static final Map<String, String> ALIASES = Map.of(
        "opus", "claude-opus-4-7",
        "sonnet", "claude-sonnet-4-6",
        "haiku", "claude-haiku-4-5"
    );

    private ModelAliases() {}

    public static String resolve(String name) {
        if (name == null || name.isBlank()) {
            return DEFAULT_MODEL;
        }
        return ALIASES.getOrDefault(name, name);
    }
}
