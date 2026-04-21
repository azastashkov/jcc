package io.jcc.tools;

import com.fasterxml.jackson.databind.JsonNode;
import io.jcc.api.ToolDefinition;
import io.jcc.core.Result;
import io.jcc.runtime.PermissionMode;
import io.jcc.runtime.ToolContext;
import io.jcc.runtime.ToolError;
import io.jcc.runtime.ToolOutput;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WriteFileTool implements BuiltinTool {

    private static final ToolDefinition SPEC = new ToolDefinition(
        "write_file",
        "Write UTF-8 text content to a file, creating or overwriting it.",
        ToolSchemas.object(
            "path", "string:Absolute or working-directory-relative file path.!",
            "content", "string:File contents to write.!"));

    @Override
    public ToolDefinition spec() {
        return SPEC;
    }

    @Override
    public PermissionMode requiredMode() {
        return PermissionMode.WORKSPACE_WRITE;
    }

    @Override
    public Result<ToolOutput, ToolError> execute(JsonNode input, ToolContext ctx) {
        if (input == null || !input.hasNonNull("path") || !input.hasNonNull("content")) {
            return Result.err(ToolError.of("write_file requires 'path' and 'content'."));
        }
        Path target = ReadFileTool.resolve(ctx.workingDir(), input.get("path").asText());
        String content = input.get("content").asText();
        try {
            Path parent = target.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(target, content);
            return Result.ok(new ToolOutput.Text("Wrote " + content.length() + " chars to " + target));
        } catch (IOException e) {
            return Result.err(ToolError.of("write_file: " + e.getMessage()));
        }
    }
}
