package io.jcc.tools;

import com.fasterxml.jackson.databind.JsonNode;
import io.jcc.api.ToolDefinition;
import io.jcc.core.Result;
import io.jcc.runtime.PermissionMode;
import io.jcc.runtime.ToolContext;
import io.jcc.runtime.ToolError;
import io.jcc.runtime.ToolOutput;

public interface BuiltinTool {

    ToolDefinition spec();

    PermissionMode requiredMode();

    Result<ToolOutput, ToolError> execute(JsonNode input, ToolContext ctx);

    default String name() {
        return spec().name();
    }
}
