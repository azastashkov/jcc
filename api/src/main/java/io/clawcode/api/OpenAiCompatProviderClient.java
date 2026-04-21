package io.clawcode.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.clawcode.core.ContentBlock;
import io.clawcode.core.JsonMapper;
import io.clawcode.core.Usage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OpenAI-compatible chat.completions provider. Supports text exchanges; tool-calls
 * passthrough is intentionally omitted in M4 and planned for M7.
 */
public final class OpenAiCompatProviderClient implements ProviderClient {

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final URI baseUrl;

    public OpenAiCompatProviderClient(String apiKey, URI baseUrl) {
        this(apiKey, baseUrl,
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build(),
            JsonMapper.shared());
    }

    public OpenAiCompatProviderClient(String apiKey, URI baseUrl, HttpClient httpClient, ObjectMapper mapper) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.httpClient = httpClient;
        this.mapper = mapper;
    }

    @Override
    public void stream(MessageRequest request, StreamEventHandler handler) {
        String body;
        try {
            body = mapper.writeValueAsString(toOpenAi(request.withStream(true)));
        } catch (JsonProcessingException e) {
            throw new ApiException("Failed to serialize OpenAI-compat request", e);
        }

        HttpRequest.Builder req = HttpRequest.newBuilder()
            .uri(baseUrl.resolve("/v1/chat/completions"))
            .header("content-type", "application/json")
            .header("accept", "text/event-stream")
            .timeout(Duration.ofMinutes(10))
            .POST(HttpRequest.BodyPublishers.ofString(body));
        if (apiKey != null && !apiKey.isBlank()) {
            req.header("authorization", "Bearer " + apiKey);
        }

        OpenAiStreamTranslator translator = new OpenAiStreamTranslator(mapper, handler, request.model());
        LineSubscriber subscriber = new LineSubscriber(translator);

        try {
            HttpResponse<Void> response = httpClient.send(req.build(), BodyHandlers.fromLineSubscriber(subscriber));
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new ProviderException("OpenAI-compat returned HTTP " + status);
            }
            translator.close();
            if (subscriber.failure() != null) {
                throw new ProviderException("Stream terminated with error", subscriber.failure());
            }
        } catch (IOException e) {
            throw new ProviderException("Request to OpenAI-compat endpoint failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderException("Request interrupted", e);
        }
    }

    ObjectNode toOpenAi(MessageRequest req) {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", req.model());
        root.put("max_tokens", req.maxTokens());
        root.put("stream", req.stream());
        if (req.temperature() != null) root.put("temperature", req.temperature());
        if (req.topP() != null) root.put("top_p", req.topP());
        if (req.frequencyPenalty() != null) root.put("frequency_penalty", req.frequencyPenalty());
        if (req.presencePenalty() != null) root.put("presence_penalty", req.presencePenalty());
        if (req.stop() != null && !req.stop().isEmpty()) {
            ArrayNode stop = root.putArray("stop");
            req.stop().forEach(stop::add);
        }
        if (req.reasoningEffort() != null) root.put("reasoning_effort", req.reasoningEffort());

        ArrayNode messages = root.putArray("messages");
        if (req.system() != null && !req.system().isBlank()) {
            ObjectNode sys = messages.addObject();
            sys.put("role", "system");
            sys.put("content", req.system());
        }
        for (InputMessage m : req.messages()) {
            String flatText = flatten(m.content());
            if (flatText.isEmpty()) continue;
            ObjectNode obj = messages.addObject();
            obj.put("role", m.role());
            obj.put("content", flatText);
        }
        return root;
    }

    private static String flatten(java.util.List<ContentBlock> blocks) {
        StringBuilder sb = new StringBuilder();
        for (ContentBlock b : blocks) {
            if (b instanceof ContentBlock.Text t) {
                sb.append(t.text());
            } else if (b instanceof ContentBlock.ToolResult r) {
                for (var inner : r.content()) {
                    if (inner instanceof io.clawcode.core.ToolResultContentBlock.Text tr) {
                        sb.append("[tool_result]\n").append(tr.text()).append('\n');
                    }
                }
            }
        }
        return sb.toString();
    }

    static final class OpenAiStreamTranslator {
        private final ObjectMapper mapper;
        private final StreamEventHandler handler;
        private final String model;
        private final StringBuilder dataBuffer = new StringBuilder();
        private boolean messageStarted;
        private boolean textBlockStarted;
        private String finishReason;
        private int outputTokens;

        OpenAiStreamTranslator(ObjectMapper mapper, StreamEventHandler handler, String model) {
            this.mapper = mapper;
            this.handler = handler;
            this.model = model;
        }

        void pushLine(String line) {
            if (line == null) return;
            if (line.isEmpty()) {
                emitFrame();
                return;
            }
            if (line.startsWith(":")) return;
            if (line.startsWith("data:")) {
                String payload = line.substring(5);
                if (!payload.isEmpty() && payload.charAt(0) == ' ') {
                    payload = payload.substring(1);
                }
                if (payload.equals("[DONE]")) {
                    finish();
                    return;
                }
                if (dataBuffer.length() > 0) dataBuffer.append('\n');
                dataBuffer.append(payload);
            }
        }

        void close() {
            if (dataBuffer.length() > 0) emitFrame();
            finish();
        }

        private void emitFrame() {
            if (dataBuffer.length() == 0) return;
            String json = dataBuffer.toString();
            dataBuffer.setLength(0);
            try {
                JsonNode root = mapper.readTree(json);
                translateChunk(root);
            } catch (IOException e) {
                throw new ProviderException("Malformed OpenAI-compat chunk: " + json, e);
            }
        }

        private void translateChunk(JsonNode chunk) {
            if (!messageStarted) {
                MessageResponse initial = new MessageResponse(
                    chunk.path("id").asText("chatcmpl"),
                    "message", "assistant",
                    java.util.List.of(), model,
                    null, null, Usage.EMPTY, null);
                handler.onEvent(new StreamEvent.MessageStart(initial));
                messageStarted = true;
            }

            JsonNode choices = chunk.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode choice = choices.get(0);
                JsonNode delta = choice.path("delta");
                JsonNode contentNode = delta.path("content");
                if (contentNode.isTextual() && !contentNode.asText().isEmpty()) {
                    if (!textBlockStarted) {
                        handler.onEvent(new StreamEvent.ContentBlockStart(0, new ContentBlock.Text("")));
                        textBlockStarted = true;
                    }
                    handler.onEvent(new StreamEvent.ContentBlockDeltaEvent(0,
                        new ContentBlockDelta.TextDelta(contentNode.asText())));
                }
                if (choice.hasNonNull("finish_reason")) {
                    finishReason = choice.get("finish_reason").asText();
                }
            }

            JsonNode usage = chunk.path("usage");
            if (usage.isObject() && usage.has("completion_tokens")) {
                outputTokens = usage.get("completion_tokens").asInt();
            }
        }

        private void finish() {
            if (!messageStarted) return;
            if (textBlockStarted) {
                handler.onEvent(new StreamEvent.ContentBlockStop(0));
                textBlockStarted = false;
            }
            String stop = finishReason == null ? "end_turn" : translateFinish(finishReason);
            handler.onEvent(new StreamEvent.MessageDeltaEvent(
                new StreamEvent.MessageDelta(stop, null),
                new Usage(0, 0, 0, outputTokens)));
            handler.onEvent(StreamEvent.MessageStop.INSTANCE);
            messageStarted = false;
            finishReason = null;
            outputTokens = 0;
        }

        private static String translateFinish(String openAi) {
            return switch (openAi) {
                case "stop" -> "end_turn";
                case "length" -> "max_tokens";
                case "tool_calls" -> "tool_use";
                case "content_filter" -> "refusal";
                default -> openAi;
            };
        }
    }

    private static final class LineSubscriber implements Flow.Subscriber<String> {
        private final OpenAiStreamTranslator translator;
        private final AtomicBoolean done = new AtomicBoolean();
        private volatile Throwable failure;

        LineSubscriber(OpenAiStreamTranslator translator) {
            this.translator = translator;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(String item) {
            if (done.get()) return;
            translator.pushLine(item);
        }

        @Override
        public void onError(Throwable throwable) {
            failure = throwable;
            done.set(true);
        }

        @Override
        public void onComplete() {
            done.set(true);
        }

        Throwable failure() {
            return failure;
        }
    }
}
