package io.clawcode.tools;

import com.fasterxml.jackson.databind.JsonNode;
import io.clawcode.api.ToolDefinition;
import io.clawcode.core.Result;
import io.clawcode.runtime.PermissionMode;
import io.clawcode.runtime.ToolContext;
import io.clawcode.runtime.ToolError;
import io.clawcode.runtime.ToolOutput;

public interface BuiltinTool {

    ToolDefinition spec();

    PermissionMode requiredMode();

    Result<ToolOutput, ToolError> execute(JsonNode input, ToolContext ctx);

    default String name() {
        return spec().name();
    }
}
