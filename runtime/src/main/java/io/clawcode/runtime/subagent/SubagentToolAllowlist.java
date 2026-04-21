package io.clawcode.runtime.subagent;

import java.util.Map;
import java.util.Set;

public final class SubagentToolAllowlist {

    private static final Set<String> DEFAULT_TOOLS = Set.of(
        "bash", "read_file", "write_file", "edit_file",
        "glob", "grep", "web_fetch", "web_search");

    private static final Map<String, Set<String>> BY_TYPE = Map.of(
        "Explore", Set.of("read_file", "glob", "grep", "web_fetch", "web_search"),
        "Plan", Set.of("read_file", "glob", "grep", "web_fetch", "web_search"),
        "Verification", Set.of("bash", "read_file", "glob", "grep", "web_fetch", "web_search"),
        "claw-guide", Set.of("read_file", "glob", "grep", "web_fetch", "web_search"),
        "statusline-setup", Set.of("bash", "read_file", "write_file", "edit_file", "glob", "grep"));

    private SubagentToolAllowlist() {}

    public static Set<String> allowedTools(String subagentType) {
        if (subagentType == null || subagentType.isBlank()) {
            return DEFAULT_TOOLS;
        }
        return BY_TYPE.getOrDefault(subagentType, DEFAULT_TOOLS);
    }

    public static String normalizeType(String subagentType) {
        if (subagentType == null) return "";
        return subagentType.trim();
    }
}
