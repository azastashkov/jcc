package io.jcc.runtime.mcp;

import com.fasterxml.jackson.databind.JsonNode;

public record McpTool(String serverName, String toolName, String description, JsonNode inputSchema) {

    public String qualifiedName() {
        return "mcp__" + serverName + "__" + toolName;
    }
}
