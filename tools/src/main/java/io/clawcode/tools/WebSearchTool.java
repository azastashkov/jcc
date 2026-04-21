package io.clawcode.tools;

import com.fasterxml.jackson.databind.JsonNode;
import io.clawcode.api.ToolDefinition;
import io.clawcode.core.Result;
import io.clawcode.runtime.PermissionMode;
import io.clawcode.runtime.ToolContext;
import io.clawcode.runtime.ToolError;
import io.clawcode.runtime.ToolOutput;

public final class WebSearchTool implements BuiltinTool {

    private static final ToolDefinition SPEC = new ToolDefinition(
        "web_search",
        "Search the web for a query. Returns up to 10 result snippets. "
            + "(Stub until M7: prefer web_fetch against a known URL.)",
        ToolSchemas.object(
            "query", "string:The search query.!"));

    @Override
    public ToolDefinition spec() {
        return SPEC;
    }

    @Override
    public PermissionMode requiredMode() {
        return PermissionMode.READ_ONLY;
    }

    @Override
    public Result<ToolOutput, ToolError> execute(JsonNode input, ToolContext ctx) {
        return Result.err(ToolError.of(
            "web_search is not yet implemented in this build (planned for M7). "
                + "Use web_fetch against a known URL instead."));
    }
}
