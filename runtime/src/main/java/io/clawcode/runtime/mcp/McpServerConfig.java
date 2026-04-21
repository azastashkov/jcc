package io.clawcode.runtime.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpServerConfig(
    String type,
    String command,
    List<String> args,
    Map<String, String> env,
    Long initializeTimeoutMs,
    Long toolsListTimeoutMs,
    Long toolCallTimeoutMs
) {

    public McpServerConfig {
        args = args == null ? List.of() : List.copyOf(args);
        env = env == null ? Map.of() : Map.copyOf(env);
    }

    public long initializeTimeoutMsOr(long fallback) {
        return initializeTimeoutMs != null ? initializeTimeoutMs : fallback;
    }

    public long toolsListTimeoutMsOr(long fallback) {
        return toolsListTimeoutMs != null ? toolsListTimeoutMs : fallback;
    }

    public long toolCallTimeoutMsOr(long fallback) {
        return toolCallTimeoutMs != null ? toolCallTimeoutMs : fallback;
    }
}
