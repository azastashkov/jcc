package io.jcc.tools;

import com.fasterxml.jackson.databind.JsonNode;
import io.jcc.api.ToolDefinition;
import io.jcc.core.Result;
import io.jcc.runtime.PermissionMode;
import io.jcc.runtime.ToolContext;
import io.jcc.runtime.ToolError;
import io.jcc.runtime.ToolExecutor;
import io.jcc.runtime.ToolOutput;

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

    public static List<BuiltinTool> withAgent(
        io.jcc.runtime.subagent.SubagentExecutor subagentExecutor) {
        List<BuiltinTool> all = new java.util.ArrayList<>(defaults());
        all.add(new AgentTool(subagentExecutor));
        return List.copyOf(all);
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

    public PermissionPolicyBuilder applyRequirementsTo(io.jcc.runtime.PermissionPolicy basis) {
        return new PermissionPolicyBuilder(basis, tools.values());
    }

    public static final class PermissionPolicyBuilder {
        private final io.jcc.runtime.PermissionPolicy basis;
        private final Iterable<BuiltinTool> tools;

        PermissionPolicyBuilder(io.jcc.runtime.PermissionPolicy basis, Iterable<BuiltinTool> tools) {
            this.basis = basis;
            this.tools = tools;
        }

        public io.jcc.runtime.PermissionPolicy build() {
            io.jcc.runtime.PermissionPolicy p = basis;
            for (BuiltinTool t : tools) {
                p = p.withToolRequirement(t.name(), t.requiredMode());
            }
            return p;
        }
    }
}
