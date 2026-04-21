package io.clawcode.tools;

import com.fasterxml.jackson.databind.JsonNode;
import io.clawcode.api.ToolDefinition;
import io.clawcode.core.Result;
import io.clawcode.runtime.PermissionMode;
import io.clawcode.runtime.ToolContext;
import io.clawcode.runtime.ToolError;
import io.clawcode.runtime.ToolOutput;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class WebFetchTool implements BuiltinTool {

    private static final ToolDefinition SPEC = new ToolDefinition(
        "web_fetch",
        "Fetch a URL via HTTP GET. Returns up to 5 MB of body; only http/https are permitted.",
        ToolSchemas.object(
            "url", "string:HTTP or HTTPS URL to fetch.!"));

    private static final int MAX_BODY_BYTES = 5 * 1024 * 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

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
        if (input == null || !input.hasNonNull("url")) {
            return Result.err(ToolError.of("web_fetch requires a 'url' string."));
        }
        URI uri;
        try {
            uri = new URI(input.get("url").asText());
        } catch (URISyntaxException e) {
            return Result.err(ToolError.of("web_fetch: invalid URL"));
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
            return Result.err(ToolError.of("web_fetch: only http/https URLs are permitted"));
        }

        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(TIMEOUT)
            .header("accept", "text/*,application/json;q=0.9,*/*;q=0.5")
            .header("user-agent", "clawcode/0.1")
            .GET()
            .build();

        try {
            HttpResponse<byte[]> response = ctx.webHttp()
                .send(request, HttpResponse.BodyHandlers.ofByteArray());
            byte[] body = response.body();
            if (body.length > MAX_BODY_BYTES) {
                return Result.err(ToolError.of(
                    "web_fetch: body exceeds 5 MB cap (" + body.length + " bytes)"));
            }
            String text = new String(body, StandardCharsets.UTF_8);
            if (response.statusCode() >= 400) {
                return Result.err(ToolError.of(
                    "web_fetch: HTTP " + response.statusCode() + "\n" + truncate(text, 2000)));
            }
            return Result.ok(new ToolOutput.Text(text));
        } catch (IOException e) {
            return Result.err(ToolError.of("web_fetch: " + e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.err(ToolError.of("web_fetch: interrupted"));
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
