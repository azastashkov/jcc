package io.clawcode.tools;

import com.fasterxml.jackson.databind.JsonNode;
import io.clawcode.api.ToolDefinition;
import io.clawcode.core.Result;
import io.clawcode.runtime.PermissionMode;
import io.clawcode.runtime.ToolContext;
import io.clawcode.runtime.ToolError;
import io.clawcode.runtime.ToolExecutor;
import io.clawcode.runtime.ToolOutput;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BuiltinToolRegistry implements ToolExecutor {

    private final Map<String, BuiltinTool> tools;

    public BuiltinToolRegistry() {
        this(defaults());
    }

    public BuiltinToolRegistry(List<BuiltinTool> tools) {
        LinkedHashMap<String, BuiltinTool> map = new LinkedHashMap<>();
        for (BuiltinTool t : tools) {
            map.put(t.name(), t);
        }
        this.tools = Map.copyOf(map);
    }

    public static List<BuiltinTool> defaults() {
        return List.of(
            new BashTool(),
            new ReadFileTool(),
            new WriteFileTool(),
            new EditFileTool(),
            new GlobTool(),
            new GrepTool(),
            new WebFetchTool(),
            new WebSearchTool());
    }

    @Override
    public List<ToolDefinition> toolDefinitions() {
        return tools.values().stream().map(BuiltinTool::spec).toList();
    }

    @Override
    public Result<ToolOutput, ToolError> execute(String name, JsonNode input, ToolContext ctx) {
        BuiltinTool tool = tools.get(name);
        if (tool == null) {
            return Result.err(ToolError.of("Unknown tool: " + name));
        }
        try {
            return tool.execute(input, ctx);
        } catch (RuntimeException e) {
            return Result.err(ToolError.of(name + ": " + e.getClass().getSimpleName()
                + ": " + e.getMessage()));
        }
    }

    @Override
    public PermissionMode requiredMode(String name) {
        BuiltinTool tool = tools.get(name);
        return tool != null ? tool.requiredMode() : PermissionMode.DANGER_FULL_ACCESS;
    }

    public PermissionPolicyBuilder applyRequirementsTo(io.clawcode.runtime.PermissionPolicy basis) {
        return new PermissionPolicyBuilder(basis, tools.values());
    }

    public static final class PermissionPolicyBuilder {
        private final io.clawcode.runtime.PermissionPolicy basis;
        private final Iterable<BuiltinTool> tools;

        PermissionPolicyBuilder(io.clawcode.runtime.PermissionPolicy basis, Iterable<BuiltinTool> tools) {
            this.basis = basis;
            this.tools = tools;
        }

        public io.clawcode.runtime.PermissionPolicy build() {
            io.clawcode.runtime.PermissionPolicy p = basis;
            for (BuiltinTool t : tools) {
                p = p.withToolRequirement(t.name(), t.requiredMode());
            }
            return p;
        }
    }
}
