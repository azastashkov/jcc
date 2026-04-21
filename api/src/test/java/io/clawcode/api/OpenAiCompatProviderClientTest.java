package io.clawcode.api;

import com.sun.net.httpserver.HttpServer;
import io.clawcode.core.JsonMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiCompatProviderClientTest {

    private HttpServer server;
    private URI baseUri;
    private final List<String> receivedBodies = new ArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            try (var in = exchange.getRequestBody()) {
                receivedBodies.add(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
            exchange.getResponseHeaders().add("content-type", "text/event-stream");
            String body = """
                data: {"id":"chatcmpl-1","choices":[{"delta":{"role":"assistant","content":"Hello"},"index":0,"finish_reason":null}]}

                data: {"id":"chatcmpl-1","choices":[{"delta":{"content":", world!"},"index":0,"finish_reason":null}]}

                data: {"id":"chatcmpl-1","choices":[{"delta":{},"index":0,"finish_reason":"stop"}],"usage":{"completion_tokens":7}}

                data: [DONE]

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
        if (server != null) server.stop(0);
    }

    @Test
    void translatesTextCompletionBackToAnthropicEvents() {
        OpenAiCompatProviderClient client = new OpenAiCompatProviderClient(
            "key",
            baseUri,
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
            JsonMapper.shared());

        MessageRequest req = MessageRequest.builder()
            .model("gpt-4o")
            .maxTokens(256)
            .system("You are a helper.")
            .messages(List.of(InputMessage.userText("say hi")))
            .build();

        List<StreamEvent> events = new ArrayList<>();
        client.stream(req, events::add);

        assertThat(events).extracting(Object::getClass).contains(
            StreamEvent.MessageStart.class,
            StreamEvent.ContentBlockStart.class,
            StreamEvent.ContentBlockDeltaEvent.class,
            StreamEvent.ContentBlockStop.class,
            StreamEvent.MessageDeltaEvent.class,
            StreamEvent.MessageStop.class);

        StreamEvent.ContentBlockDeltaEvent firstDelta = (StreamEvent.ContentBlockDeltaEvent) events.stream()
            .filter(e -> e instanceof StreamEvent.ContentBlockDeltaEvent)
            .findFirst().orElseThrow();
        assertThat(((ContentBlockDelta.TextDelta) firstDelta.delta()).text()).isEqualTo("Hello");

        StreamEvent.MessageDeltaEvent md = (StreamEvent.MessageDeltaEvent) events.stream()
            .filter(e -> e instanceof StreamEvent.MessageDeltaEvent)
            .findFirst().orElseThrow();
        assertThat(md.delta().stopReason()).isEqualTo("end_turn");
        assertThat(md.usage().outputTokens()).isEqualTo(7);

        assertThat(receivedBodies).hasSize(1);
        assertThat(receivedBodies.get(0)).contains("\"model\":\"gpt-4o\"")
            .contains("\"role\":\"system\"")
            .contains("You are a helper.")
            .contains("\"content\":\"say hi\"");
    }
}
