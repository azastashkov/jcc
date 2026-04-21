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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

public final class GrepTool implements BuiltinTool {

    private static final ToolDefinition SPEC = new ToolDefinition(
        "grep",
        "Search file contents for a regular expression. Returns matching 'file:line: text' lines, "
            + "up to 200 hits.",
        ToolSchemas.object(
            "pattern", "string:Java-style regular expression.!",
            "path", "string:Optional root directory to search (defaults to the working directory).",
            "glob", "string:Optional file-path glob to restrict the search (e.g. '**/*.java').",
            "case_insensitive", "boolean:If true, perform a case-insensitive match."));

    private static final int MAX_HITS = 200;

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
            return Result.err(ToolError.of("grep requires a 'pattern' string."));
        }
        Path root = input.hasNonNull("path")
            ? ReadFileTool.resolve(ctx.workingDir(), input.get("path").asText())
            : ctx.workingDir();
        boolean ci = input.hasNonNull("case_insensitive") && input.get("case_insensitive").asBoolean();
        Pattern regex;
        try {
            regex = Pattern.compile(input.get("pattern").asText(),
                ci ? Pattern.CASE_INSENSITIVE : 0);
        } catch (PatternSyntaxException e) {
            return Result.err(ToolError.of("grep: invalid regex: " + e.getDescription()));
        }

        PathMatcher globMatcher = input.hasNonNull("glob")
            ? FileSystems.getDefault().getPathMatcher("glob:" + input.get("glob").asText())
            : null;

        List<String> hits = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> files = stream
                .filter(Files::isRegularFile)
                .filter(p -> globMatcher == null || globMatcher.matches(root.relativize(p)))
                .toList();
            for (Path file : files) {
                try {
                    List<String> lines = Files.readAllLines(file);
                    for (int i = 0; i < lines.size() && hits.size() < MAX_HITS; i++) {
                        if (regex.matcher(lines.get(i)).find()) {
                            hits.add(file + ":" + (i + 1) + ": " + lines.get(i));
                        }
                    }
                } catch (IOException ignored) {
                    // skip unreadable / binary files
                }
                if (hits.size() >= MAX_HITS) break;
            }
        } catch (IOException e) {
            return Result.err(ToolError.of("grep: " + e.getMessage()));
        }

        if (hits.isEmpty()) {
            return Result.ok(new ToolOutput.Text("No matches."));
        }
        return Result.ok(new ToolOutput.Text(String.join("\n", hits)));
    }
}
