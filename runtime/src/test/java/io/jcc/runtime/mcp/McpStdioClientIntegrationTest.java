package io.jcc.runtime.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import io.jcc.core.JsonMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class McpStdioClientIntegrationTest {

    private java.util.concurrent.ExecutorService executor;

    @BeforeEach
    void setup() {
        executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void initializeListToolsAndCallRoundTrip(@TempDir Path tmp) throws IOException {
        Path script = tmp.resolve("mock-mcp.sh");
        Files.writeString(script, """
            #!/bin/bash
            while IFS= read -r line; do
              id_field=$(printf '%s' "$line" | sed -n 's/.*"id":"\\([^"]*\\)".*/\\1/p')
              if printf '%s' "$line" | grep -q '"method":"initialize"'; then
                printf '{"jsonrpc":"2.0","id":"%s","result":{"protocolVersion":"2024-11-05","capabilities":{}}}\\n' "$id_field"
              elif printf '%s' "$line" | grep -q '"method":"tools/list"'; then
                printf '{"jsonrpc":"2.0","id":"%s","result":{"tools":[{"name":"echo","description":"Echo the message","inputSchema":{"type":"object","properties":{"message":{"type":"string"}}}}]}}\\n' "$id_field"
              elif printf '%s' "$line" | grep -q '"method":"tools/call"'; then
                printf '{"jsonrpc":"2.0","id":"%s","result":{"content":[{"type":"text","text":"pong"}]}}\\n' "$id_field"
              fi
            done
            """);
        Set<PosixFilePermission> perms = EnumSet.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
        Files.setPosixFilePermissions(script, perms);

        McpServerConfig config = new McpServerConfig(
            "stdio", "/bin/bash", List.of(script.toString()),
            null, 5_000L, 5_000L, 5_000L);

        try (McpStdioClient client = McpStdioClient.spawn("mock", config, executor)) {
            JsonNode init = client.initialize();
            assertThat(init.path("protocolVersion").asText()).isEqualTo("2024-11-05");

            JsonNode tools = client.listTools();
            assertThat(tools.path("tools").isArray()).isTrue();
            assertThat(tools.path("tools").get(0).path("name").asText()).isEqualTo("echo");

            JsonNode call = client.callTool("echo",
                JsonMapper.shared().readTree("{\"message\":\"hi\"}"));
            assertThat(call.path("content").get(0).path("text").asText()).isEqualTo("pong");
        }
    }

    @Test
    void timeoutIsReportedCleanly(@TempDir Path tmp) throws IOException {
        Path script = tmp.resolve("silent.sh");
        Files.writeString(script, """
            #!/bin/bash
            # Drain stdin but never reply.
            cat > /dev/null
            """);
        Files.setPosixFilePermissions(script, Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE));

        McpServerConfig config = new McpServerConfig(
            "stdio", "/bin/bash", List.of(script.toString()),
            null, 300L, 300L, 300L);

        try (McpStdioClient client = McpStdioClient.spawn("silent", config, executor)) {
            org.assertj.core.api.Assertions.assertThatThrownBy(client::initialize)
                .isInstanceOf(McpTransportException.class)
                .hasMessageContaining("timed out");
        }
    }
}
