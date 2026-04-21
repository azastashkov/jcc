package io.clawcode.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import io.clawcode.api.ToolDefinition;
import io.clawcode.core.Result;

import java.util.List;

public interface ToolExecutor {

    List<ToolDefinition> toolDefinitions();

    Result<ToolOutput, ToolError> execute(String name, JsonNode input, ToolContext ctx);

    PermissionMode requiredMode(String name);
}
