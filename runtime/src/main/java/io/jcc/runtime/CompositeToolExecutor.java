package io.jcc.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import io.jcc.api.ToolDefinition;
import io.jcc.core.Result;
import io.jcc.runtime.mcp.McpServerManager;

import java.util.ArrayList;
import java.util.List;

public final class CompositeToolExecutor implements ToolExecutor {

    private final ToolExecutor primary;
    private final McpServerManager mcp;

    public CompositeToolExecutor(ToolExecutor primary, McpServerManager mcp) {
        this.primary = primary;
        this.mcp = mcp;
    }

    @Override
    public List<ToolDefinition> toolDefinitions() {
        List<ToolDefinition> all = new ArrayList<>(primary.toolDefinitions());
        if (mcp != null) {
            all.addAll(mcp.toolDefinitions());
        }
        return all;
    }

    @Override
    public Result<ToolOutput, ToolError> execute(String name, JsonNode input, ToolContext ctx) {
        if (mcp != null && mcp.handles(name)) {
            return mcp.execute(name, input);
        }
        return primary.execute(name, input, ctx);
    }

    @Override
    public PermissionMode requiredMode(String name) {
        if (mcp != null && mcp.handles(name)) {
            return PermissionMode.WORKSPACE_WRITE;
        }
        return primary.requiredMode(name);
    }
}
