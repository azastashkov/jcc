package io.jcc.cli;

import com.sun.net.httpserver.HttpServer;
import io.jcc.api.AnthropicProviderClient;
import io.jcc.api.ProviderClient;
import io.jcc.core.JsonMapper;
import io.jcc.runtime.SessionStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
    void textOutputStreamsDeltasAndUsage(@TempDir Path sessionsDir) {
        PromptSubcommand cmd = newCommand("text");
        cmd.prompt = "Say hello";

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int exit = cmd.runPrompt(providerClient(), new PrintStream(buffer, true, StandardCharsets.UTF_8),
            new SessionStore(sessionsDir));

        assertThat(exit).isZero();
        String out = buffer.toString(StandardCharsets.UTF_8);
        assertThat(out).contains("Hello, world!");
        assertThat(out).contains("tokens in=12 out=7");
    }

    @Test
    void jsonOutputEmitsNdjson(@TempDir Path sessionsDir) {
        PromptSubcommand cmd = newCommand("json");
        cmd.prompt = "Say hello";

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int exit = cmd.runPrompt(providerClient(), new PrintStream(buffer, true, StandardCharsets.UTF_8),
            new SessionStore(sessionsDir));

        assertThat(exit).isZero();
        String out = buffer.toString(StandardCharsets.UTF_8);
        String[] lines = out.trim().split("\n");
        assertThat(lines.length).isGreaterThanOrEqualTo(3);
        assertThat(lines[0]).contains("\"text\":\"Hello\"");
        assertThat(out).contains("turn_finish");
        assertThat(out).contains("usage_report");
    }

    @Test
    void resumeCarriesPriorHistory(@TempDir Path sessionsDir) {
        SessionStore store = new SessionStore(sessionsDir);

        PromptSubcommand first = newCommand("text");
        first.prompt = "first message";
        first.runPrompt(providerClient(), silent(), store);

        Path firstSession = store.list().get(0);
        long sizeAfterFirst = firstSession.toFile().length();
        assertThat(sizeAfterFirst).isGreaterThan(0L);

        PromptSubcommand second = newCommand("text");
        second.prompt = "second message";
        second.resume = firstSession.getFileName().toString();
        second.runPrompt(providerClient(), silent(), store);

        long sizeAfterSecond = firstSession.toFile().length();
        assertThat(sizeAfterSecond).isGreaterThan(sizeAfterFirst);
    }

    private PromptSubcommand newCommand(String format) {
        PromptSubcommand cmd = new PromptSubcommand();
        cmd.model = "opus";
        cmd.outputFormat = format;
        cmd.maxTokens = 1024;
        cmd.permissionMode = "danger-full-access";
        return cmd;
    }

    private ProviderClient providerClient() {
        return new AnthropicProviderClient(
            "test-key",
            baseUri,
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
            JsonMapper.shared());
    }

    private PrintStream silent() {
        return new PrintStream(OutputStream.nullOutputStream(), true, StandardCharsets.UTF_8);
    }
}
