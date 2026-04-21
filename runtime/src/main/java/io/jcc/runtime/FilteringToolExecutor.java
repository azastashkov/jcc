package io.jcc.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import io.jcc.api.ToolDefinition;
import io.jcc.core.Result;

import java.util.List;
import java.util.Set;

public final class FilteringToolExecutor implements ToolExecutor {

    private final ToolExecutor delegate;
    private final Set<String> allowedNames;

    public FilteringToolExecutor(ToolExecutor delegate, Set<String> allowedNames) {
        this.delegate = delegate;
        this.allowedNames = Set.copyOf(allowedNames);
    }

    @Override
    public List<ToolDefinition> toolDefinitions() {
        return delegate.toolDefinitions().stream()
            .filter(def -> allowedNames.contains(def.name()))
            .toList();
    }

    @Override
    public Result<ToolOutput, ToolError> execute(String name, JsonNode input, ToolContext ctx) {
        if (!allowedNames.contains(name)) {
            return Result.err(ToolError.of(
                "Tool '" + name + "' is not available to this sub-agent."));
        }
        return delegate.execute(name, input, ctx);
    }

    @Override
    public PermissionMode requiredMode(String name) {
        return delegate.requiredMode(name);
    }
}
