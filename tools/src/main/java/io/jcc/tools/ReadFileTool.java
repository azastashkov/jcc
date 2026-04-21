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
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

public final class ReadFileTool implements BuiltinTool {

    private static final ToolDefinition SPEC = new ToolDefinition(
        "read_file",
        "Read a UTF-8 text file. Returns the contents up to a 1 MB cap.",
        ToolSchemas.object(
            "path", "string:Absolute or working-directory-relative file path.!",
            "offset", "integer:Optional starting line (1-indexed).",
            "limit", "integer:Optional maximum number of lines to return."));

    private static final long MAX_BYTES = 1024L * 1024L;

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
        if (input == null || !input.hasNonNull("path")) {
            return Result.err(ToolError.of("read_file requires a 'path' string."));
        }
        Path target = resolve(ctx.workingDir(), input.get("path").asText());
        try {
            long size = Files.size(target);
            if (size > MAX_BYTES) {
                return Result.err(ToolError.of(
                    "read_file: file exceeds 1 MB cap (" + size + " bytes)"));
            }
            String content = Files.readString(target);
            int offset = input.hasNonNull("offset") ? Math.max(1, input.get("offset").asInt()) : 1;
            int limit = input.hasNonNull("limit") ? input.get("limit").asInt() : Integer.MAX_VALUE;
            if (offset == 1 && limit == Integer.MAX_VALUE) {
                return Result.ok(new ToolOutput.Text(content));
            }
            String[] lines = content.split("\n", -1);
            int from = Math.min(offset - 1, lines.length);
            int to = Math.min(from + limit, lines.length);
            return Result.ok(new ToolOutput.Text(String.join("\n",
                java.util.Arrays.copyOfRange(lines, from, to))));
        } catch (NoSuchFileException e) {
            return Result.err(ToolError.of("read_file: no such file: " + target));
        } catch (IOException e) {
            return Result.err(ToolError.of("read_file: " + e.getMessage()));
        }
    }

    static Path resolve(Path workingDir, String raw) {
        Path p = Path.of(raw);
        if (p.isAbsolute()) return p;
        return workingDir.resolve(p).normalize();
    }
}
