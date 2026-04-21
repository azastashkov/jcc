package io.jcc.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import io.jcc.core.ContentBlock;
import io.jcc.core.JsonMapper;
import io.jcc.core.ToolResultContentBlock;
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

    private static final String TEXT_RESPONSE = """
        data: {"id":"chatcmpl-1","choices":[{"delta":{"role":"assistant","content":"Hello"},"index":0,"finish_reason":null}]}

        data: {"id":"chatcmpl-1","choices":[{"delta":{"content":", world!"},"index":0,"finish_reason":null}]}

        data: {"id":"chatcmpl-1","choices":[{"delta":{},"index":0,"finish_reason":"stop"}],"usage":{"completion_tokens":7}}

        data: [DONE]

        """;

    private HttpServer server;
    private URI baseUri;
    private final List<String> receivedBodies = new ArrayList<>();
    private volatile String responseBody = TEXT_RESPONSE;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            try (var in = exchange.getRequestBody()) {
                receivedBodies.add(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
            exchange.getResponseHeaders().add("content-type", "text/event-stream");
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
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

    private OpenAiCompatProviderClient client() {
        return new OpenAiCompatProviderClient(
            "key",
            baseUri,
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
            JsonMapper.shared());
    }

    @Test
    void translatesTextCompletionBackToAnthropicEvents() {
        OpenAiCompatProviderClient client = client();

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

    @Test
    void advertisesToolsAsOpenAiFunctions() throws Exception {
        ObjectNode schema = JsonMapper.shared().createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        ObjectNode pathProp = props.putObject("path");
        pathProp.put("type", "string");

        MessageRequest req = MessageRequest.builder()
            .model("qwen")
            .maxTokens(100)
            .messages(List.of(InputMessage.userText("hi")))
            .tools(List.of(new ToolDefinition("read_file", "Read a file", schema)))
            .build();

        client().stream(req, event -> {});

        assertThat(receivedBodies).hasSize(1);
        JsonNode body = JsonMapper.shared().readTree(receivedBodies.get(0));
        JsonNode tools = body.path("tools");
        assertThat(tools.isArray()).isTrue();
        assertThat(tools).hasSize(1);
        JsonNode tool = tools.get(0);
        assertThat(tool.path("type").asText()).isEqualTo("function");
        assertThat(tool.path("function").path("name").asText()).isEqualTo("read_file");
        assertThat(tool.path("function").path("description").asText()).isEqualTo("Read a file");
        assertThat(tool.path("function").path("parameters").path("type").asText()).isEqualTo("object");
        assertThat(tool.path("function").path("parameters").path("properties").path("path").path("type").asText())
            .isEqualTo("string");
    }

    @Test
    void translatesStreamedToolCallsIntoToolUseEvents() {
        responseBody = """
            data: {"id":"chatcmpl-1","choices":[{"delta":{"role":"assistant","tool_calls":[{"index":0,"id":"call_abc","type":"function","function":{"name":"read_file","arguments":""}}]},"index":0,"finish_reason":null}]}

            data: {"id":"chatcmpl-1","choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\\"path\\":"}}]},"index":0,"finish_reason":null}]}

            data: {"id":"chatcmpl-1","choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\\"./README.md\\"}"}}]},"index":0,"finish_reason":null}]}

            data: {"id":"chatcmpl-1","choices":[{"delta":{},"index":0,"finish_reason":"tool_calls"}],"usage":{"completion_tokens":12}}

            data: [DONE]

            """;

        MessageRequest req = MessageRequest.builder()
            .model("qwen")
            .maxTokens(100)
            .messages(List.of(InputMessage.userText("read the readme")))
            .build();

        List<StreamEvent> events = new ArrayList<>();
        client().stream(req, events::add);

        StreamEvent.ContentBlockStart toolStart = (StreamEvent.ContentBlockStart) events.stream()
            .filter(e -> e instanceof StreamEvent.ContentBlockStart cbs
                && cbs.contentBlock() instanceof ContentBlock.ToolUse)
            .findFirst().orElseThrow();
        ContentBlock.ToolUse use = (ContentBlock.ToolUse) toolStart.contentBlock();
        assertThat(use.id()).isEqualTo("call_abc");
        assertThat(use.name()).isEqualTo("read_file");

        String accumulatedArgs = events.stream()
            .filter(e -> e instanceof StreamEvent.ContentBlockDeltaEvent cbd
                && cbd.delta() instanceof ContentBlockDelta.InputJsonDelta)
            .map(e -> ((ContentBlockDelta.InputJsonDelta) ((StreamEvent.ContentBlockDeltaEvent) e).delta()).partialJson())
            .reduce("", String::concat);
        assertThat(accumulatedArgs).isEqualTo("{\"path\":\"./README.md\"}");

        StreamEvent.MessageDeltaEvent md = (StreamEvent.MessageDeltaEvent) events.stream()
            .filter(e -> e instanceof StreamEvent.MessageDeltaEvent)
            .findFirst().orElseThrow();
        assertThat(md.delta().stopReason()).isEqualTo("tool_use");
    }

    @Test
    void translatesHermesStyleTextualToolCallIntoToolUseEvent() {
        responseBody = """
            data: {"id":"chatcmpl-1","choices":[{"delta":{"role":"assistant","content":"<function"},"index":0,"finish_reason":null}]}

            data: {"id":"chatcmpl-1","choices":[{"delta":{"content":"=read_file>\\n<parameter=path>\\nPermissionMode.java\\n</parameter>\\n</function>\\n</tool_call>"},"index":0,"finish_reason":null}]}

            data: {"id":"chatcmpl-1","choices":[{"delta":{},"index":0,"finish_reason":"stop"}],"usage":{"completion_tokens":20}}

            data: [DONE]

            """;

        MessageRequest req = MessageRequest.builder()
            .model("qwen")
            .maxTokens(200)
            .messages(List.of(InputMessage.userText("show PermissionMode.java")))
            .build();

        List<StreamEvent> events = new ArrayList<>();
        client().stream(req, events::add);

        boolean anyTextDelta = events.stream()
            .anyMatch(e -> e instanceof StreamEvent.ContentBlockDeltaEvent cbd
                && cbd.delta() instanceof ContentBlockDelta.TextDelta);
        assertThat(anyTextDelta)
            .as("the Hermes XML should not leak through as text deltas")
            .isFalse();

        StreamEvent.ContentBlockStart toolStart = (StreamEvent.ContentBlockStart) events.stream()
            .filter(e -> e instanceof StreamEvent.ContentBlockStart cbs
                && cbs.contentBlock() instanceof ContentBlock.ToolUse)
            .findFirst().orElseThrow();
        ContentBlock.ToolUse use = (ContentBlock.ToolUse) toolStart.contentBlock();
        assertThat(use.name()).isEqualTo("read_file");
        assertThat(use.id()).isNotBlank();

        String accumulatedArgs = events.stream()
            .filter(e -> e instanceof StreamEvent.ContentBlockDeltaEvent cbd
                && cbd.delta() instanceof ContentBlockDelta.InputJsonDelta)
            .map(e -> ((ContentBlockDelta.InputJsonDelta) ((StreamEvent.ContentBlockDeltaEvent) e).delta()).partialJson())
            .reduce("", String::concat);
        JsonNode args;
        try {
            args = JsonMapper.shared().readTree(accumulatedArgs);
        } catch (IOException e) {
            throw new AssertionError("arguments not valid JSON: " + accumulatedArgs, e);
        }
        assertThat(args.path("path").asText()).isEqualTo("PermissionMode.java");

        StreamEvent.MessageDeltaEvent md = (StreamEvent.MessageDeltaEvent) events.stream()
            .filter(e -> e instanceof StreamEvent.MessageDeltaEvent)
            .findFirst().orElseThrow();
        assertThat(md.delta().stopReason()).isEqualTo("tool_use");
    }

    @Test
    void passesThroughPlainTextWhenNoHermesMarkers() {
        responseBody = """
            data: {"id":"chatcmpl-1","choices":[{"delta":{"role":"assistant","content":"Hello "},"index":0,"finish_reason":null}]}

            data: {"id":"chatcmpl-1","choices":[{"delta":{"content":"world."},"index":0,"finish_reason":null}]}

            data: {"id":"chatcmpl-1","choices":[{"delta":{},"index":0,"finish_reason":"stop"}]}

            data: [DONE]

            """;

        MessageRequest req = MessageRequest.builder()
            .model("qwen")
            .maxTokens(20)
            .messages(List.of(InputMessage.userText("greet")))
            .build();

        List<StreamEvent> events = new ArrayList<>();
        client().stream(req, events::add);

        String collected = events.stream()
            .filter(e -> e instanceof StreamEvent.ContentBlockDeltaEvent cbd
                && cbd.delta() instanceof ContentBlockDelta.TextDelta)
            .map(e -> ((ContentBlockDelta.TextDelta) ((StreamEvent.ContentBlockDeltaEvent) e).delta()).text())
            .reduce("", String::concat);
        assertThat(collected).isEqualTo("Hello world.");

        boolean anyToolUse = events.stream()
            .anyMatch(e -> e instanceof StreamEvent.ContentBlockStart cbs
                && cbs.contentBlock() instanceof ContentBlock.ToolUse);
        assertThat(anyToolUse).isFalse();
    }

    @Test
    void mapsAssistantToolUseAndToolResultHistoryToOpenAiMessages() throws Exception {
        ObjectNode toolInput = JsonMapper.shared().createObjectNode();
        toolInput.put("path", "./README.md");

        InputMessage userAsk = InputMessage.userText("read the readme");
        InputMessage assistantCall = new InputMessage("assistant", List.of(
            new ContentBlock.ToolUse("call_abc", "read_file", toolInput)));
        InputMessage toolResponse = new InputMessage("user", List.of(
            new ContentBlock.ToolResult(
                "call_abc",
                List.of(new ToolResultContentBlock.Text("# jcc\n...")),
                false)));

        MessageRequest req = MessageRequest.builder()
            .model("qwen")
            .maxTokens(100)
            .messages(List.of(userAsk, assistantCall, toolResponse))
            .tools(List.of(new ToolDefinition(
                "read_file", "Read a file", JsonMapper.shared().createObjectNode())))
            .build();

        client().stream(req, event -> {});

        JsonNode body = JsonMapper.shared().readTree(receivedBodies.get(0));
        JsonNode messages = body.path("messages");
        assertThat(messages).hasSize(3);

        assertThat(messages.get(0).path("role").asText()).isEqualTo("user");
        assertThat(messages.get(0).path("content").asText()).isEqualTo("read the readme");

        JsonNode assistant = messages.get(1);
        assertThat(assistant.path("role").asText()).isEqualTo("assistant");
        JsonNode toolCalls = assistant.path("tool_calls");
        assertThat(toolCalls.isArray()).isTrue();
        assertThat(toolCalls).hasSize(1);
        assertThat(toolCalls.get(0).path("id").asText()).isEqualTo("call_abc");
        assertThat(toolCalls.get(0).path("type").asText()).isEqualTo("function");
        assertThat(toolCalls.get(0).path("function").path("name").asText()).isEqualTo("read_file");

        String argsString = toolCalls.get(0).path("function").path("arguments").asText();
        JsonNode argsJson = JsonMapper.shared().readTree(argsString);
        assertThat(argsJson.path("path").asText()).isEqualTo("./README.md");

        JsonNode toolMsg = messages.get(2);
        assertThat(toolMsg.path("role").asText()).isEqualTo("tool");
        assertThat(toolMsg.path("tool_call_id").asText()).isEqualTo("call_abc");
        assertThat(toolMsg.path("content").asText()).isEqualTo("# jcc\n...");
    }
}
