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

public final class EditFileTool implements BuiltinTool {

    private static final ToolDefinition SPEC = new ToolDefinition(
        "edit_file",
        "Edit a file by replacing a unique string with a new one. Fails if the old string is not found "
            + "exactly once unless replace_all is true.",
        ToolSchemas.object(
            "path", "string:File path (absolute or working-directory-relative).!",
            "old_string", "string:The exact text to find.!",
            "new_string", "string:The text to replace it with.!",
            "replace_all", "boolean:If true, replace all occurrences instead of requiring exactly one."));

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
        if (input == null
            || !input.hasNonNull("path")
            || !input.hasNonNull("old_string")
            || !input.hasNonNull("new_string")) {
            return Result.err(ToolError.of(
                "edit_file requires 'path', 'old_string', and 'new_string'."));
        }
        Path target = ReadFileTool.resolve(ctx.workingDir(), input.get("path").asText());
        String oldStr = input.get("old_string").asText();
        String newStr = input.get("new_string").asText();
        boolean replaceAll = input.hasNonNull("replace_all") && input.get("replace_all").asBoolean();

        try {
            String content = Files.readString(target);
            int count = countOccurrences(content, oldStr);
            if (count == 0) {
                return Result.err(ToolError.of("edit_file: old_string not found in " + target));
            }
            if (!replaceAll && count > 1) {
                return Result.err(ToolError.of(
                    "edit_file: old_string matches " + count + " times; set replace_all=true"
                        + " to replace all or extend old_string to make the match unique."));
            }
            String updated = replaceAll
                ? content.replace(oldStr, newStr)
                : replaceFirstLiteral(content, oldStr, newStr);
            Files.writeString(target, updated);
            return Result.ok(new ToolOutput.Text(
                "Replaced " + (replaceAll ? count : 1) + " occurrence(s) in " + target));
        } catch (IOException e) {
            return Result.err(ToolError.of("edit_file: " + e.getMessage()));
        }
    }

    private static int countOccurrences(String haystack, String needle) {
        if (needle.isEmpty()) return 0;
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    private static String replaceFirstLiteral(String haystack, String needle, String replacement) {
        int idx = haystack.indexOf(needle);
        if (idx < 0) return haystack;
        return haystack.substring(0, idx) + replacement + haystack.substring(idx + needle.length());
    }
}
