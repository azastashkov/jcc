package io.jcc.tools;

import com.fasterxml.jackson.databind.JsonNode;
import io.jcc.api.ToolDefinition;
import io.jcc.core.Result;
import io.jcc.runtime.PermissionMode;
import io.jcc.runtime.ToolContext;
import io.jcc.runtime.ToolError;
import io.jcc.runtime.ToolOutput;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class BashTool implements BuiltinTool {

    private static final ToolDefinition SPEC = new ToolDefinition(
        "bash",
        "Execute a shell command via /bin/sh -c. Captures stdout and stderr.",
        ToolSchemas.object(
            "command", "string:Shell command to execute.!",
            "timeout_ms", "integer:Optional timeout in milliseconds (default 120000, max 600000)."));

    private static final long DEFAULT_TIMEOUT_MS = 120_000;
    private static final long MAX_TIMEOUT_MS = 600_000;
    private static final int MAX_OUTPUT_BYTES = 64 * 1024;

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
        if (input == null || !input.hasNonNull("command")) {
            return Result.err(ToolError.of("bash requires a 'command' string."));
        }
        String command = input.get("command").asText();
        long timeoutMs = input.hasNonNull("timeout_ms")
            ? Math.min(input.get("timeout_ms").asLong(), MAX_TIMEOUT_MS)
            : DEFAULT_TIMEOUT_MS;

        try {
            ProcessBuilder pb = new ProcessBuilder(List.of("/bin/sh", "-c", command))
                .directory(ctx.workingDir().toFile())
                .redirectErrorStream(true);
            Process proc = pb.start();
            byte[] output;
            try (var in = proc.getInputStream()) {
                output = in.readNBytes(MAX_OUTPUT_BYTES);
            }
            boolean exited = proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!exited) {
                proc.destroy();
                if (!proc.waitFor(2, TimeUnit.SECONDS)) {
                    proc.destroyForcibly();
                }
                return Result.err(ToolError.of(
                    "bash: command timed out after " + timeoutMs + "ms"));
            }
            int exit = proc.exitValue();
            String body = new String(output, StandardCharsets.UTF_8);
            if (exit == 0) {
                return Result.ok(new ToolOutput.Text(body.isEmpty() ? "(no output)" : body));
            }
            return Result.err(ToolError.of(
                "bash: exit code " + exit + (body.isEmpty() ? "" : "\n" + body)));
        } catch (IOException e) {
            return Result.err(ToolError.of("bash: " + e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.err(ToolError.of("bash: interrupted"));
        }
    }
}
