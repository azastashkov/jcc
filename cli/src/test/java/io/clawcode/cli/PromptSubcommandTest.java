package io.clawcode.cli;

import com.sun.net.httpserver.HttpServer;
import io.clawcode.api.AnthropicProviderClient;
import io.clawcode.api.ProviderClient;
import io.clawcode.core.JsonMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class PromptSubcommandTest {

    private HttpServer server;
    private URI baseUri;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            exchange.getResponseHeaders().add("content-type", "text/event-stream");
            String body = """
                event: message_start
                data: {"type":"message_start","message":{"id":"msg_1","type":"message","role":"assistant","content":[],"model":"claude-opus-4-7","stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":12,"output_tokens":0}}}

                event: content_block_start
                data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":", world!"}}

                event: content_block_stop
                data: {"type":"content_block_stop","index":0}

                event: message_delta
                data: {"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":7}}

                event: message_stop
                data: {"type":"message_stop"}

                """;
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void textOutputStreamsDeltasAndUsage() {
        ProviderClient client = new AnthropicProviderClient(
            "test-key",
            baseUri,
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
            JsonMapper.shared());

        PromptSubcommand cmd = new PromptSubcommand();
        cmd.model = "opus";
        cmd.outputFormat = "text";
        cmd.prompt = "Say hello";

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int exit = cmd.runPrompt(client, new PrintStream(buffer, true, StandardCharsets.UTF_8));

        assertThat(exit).isZero();
        String out = buffer.toString(StandardCharsets.UTF_8);
        assertThat(out).contains("Hello, world!");
        assertThat(out).contains("tokens in=12 out=7");
    }

    @Test
    void jsonOutputEmitsNdjson() {
        ProviderClient client = new AnthropicProviderClient(
            "test-key",
            baseUri,
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
            JsonMapper.shared());

        PromptSubcommand cmd = new PromptSubcommand();
        cmd.model = "opus";
        cmd.outputFormat = "json";
        cmd.prompt = "Say hello";

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int exit = cmd.runPrompt(client, new PrintStream(buffer, true, StandardCharsets.UTF_8));

        assertThat(exit).isZero();
        String out = buffer.toString(StandardCharsets.UTF_8);
        String[] lines = out.trim().split("\n");
        assertThat(lines.length).isGreaterThanOrEqualTo(3);
        assertThat(lines[0]).contains("\"text\":\"Hello\"");
        assertThat(out).contains("turn_finish");
        assertThat(out).contains("usage_report");
    }
}
