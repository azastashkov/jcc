package io.clawcode.tools;

import com.fasterxml.jackson.databind.JsonNode;
import io.clawcode.api.ToolDefinition;
import io.clawcode.core.Result;
import io.clawcode.runtime.PermissionMode;
import io.clawcode.runtime.ToolContext;
import io.clawcode.runtime.ToolError;
import io.clawcode.runtime.ToolOutput;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.stream.Stream;

public final class GlobTool implements BuiltinTool {

    private static final ToolDefinition SPEC = new ToolDefinition(
        "glob",
        "List files matching a glob pattern (e.g. 'src/**/*.java'). Returns up to 500 paths, "
            + "sorted by most-recently-modified first.",
        ToolSchemas.object(
            "pattern", "string:Glob pattern relative to the working directory.!",
            "path", "string:Optional root directory to search (defaults to the working directory)."));

    private static final int MAX_RESULTS = 500;

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
        if (input == null || !input.hasNonNull("pattern")) {
            return Result.err(ToolError.of("glob requires a 'pattern' string."));
        }
        Path root = input.hasNonNull("path")
            ? ReadFileTool.resolve(ctx.workingDir(), input.get("path").asText())
            : ctx.workingDir();
        String pattern = input.get("pattern").asText();
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        PathMatcher rootMatcher = pattern.startsWith("**/")
            ? FileSystems.getDefault().getPathMatcher("glob:" + pattern.substring(3))
            : null;

        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> matches = stream
                .filter(Files::isRegularFile)
                .filter(p -> {
                    Path rel = root.relativize(p);
                    if (matcher.matches(rel)) return true;
                    return rootMatcher != null && rel.getNameCount() == 1 && rootMatcher.matches(rel);
                })
                .sorted((a, b) -> Long.compare(lastModified(b), lastModified(a)))
                .limit(MAX_RESULTS)
                .toList();
            if (matches.isEmpty()) {
                return Result.ok(new ToolOutput.Text("No files match pattern '" + pattern + "'."));
            }
            StringBuilder sb = new StringBuilder();
            for (Path p : matches) {
                sb.append(p).append('\n');
            }
            return Result.ok(new ToolOutput.Text(sb.toString().stripTrailing()));
        } catch (IOException e) {
            return Result.err(ToolError.of("glob: " + e.getMessage()));
        }
    }

    private static long lastModified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException ignored) {
            return 0;
        }
    }
}
