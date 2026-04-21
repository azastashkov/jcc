package io.jcc.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import io.jcc.api.ToolDefinition;
import io.jcc.core.Result;

import java.util.List;

public interface ToolExecutor {

    List<ToolDefinition> toolDefinitions();

    Result<ToolOutput, ToolError> execute(String name, JsonNode input, ToolContext ctx);

    PermissionMode requiredMode(String name);
}
