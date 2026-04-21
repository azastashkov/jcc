package io.clawcode.runtime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.clawcode.runtime.mcp.McpServerConfig;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record RuntimeConfig(
    String model,
    Integer maxTokens,
    PermissionsConfig permissions,
    Map<String, McpServerConfig> mcp
) {

    public RuntimeConfig {
        permissions = permissions == null ? PermissionsConfig.empty() : permissions;
        mcp = mcp == null ? Map.of() : Map.copyOf(mcp);
    }

    public RuntimeConfig(String model, Integer maxTokens, PermissionsConfig permissions) {
        this(model, maxTokens, permissions, Map.of());
    }

    public static RuntimeConfig empty() {
        return new RuntimeConfig(null, null, PermissionsConfig.empty(), Map.of());
    }
}
