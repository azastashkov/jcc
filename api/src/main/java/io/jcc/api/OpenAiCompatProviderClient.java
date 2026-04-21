package io.jcc.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.jcc.core.ContentBlock;
import io.jcc.core.JsonMapper;
import io.jcc.core.ToolResultContentBlock;
import io.jcc.core.Usage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

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

        if (req.tools() != null && !req.tools().isEmpty()) {
            ArrayNode tools = root.putArray("tools");
            for (ToolDefinition t : req.tools()) {
                ObjectNode tool = tools.addObject();
                tool.put("type", "function");
                ObjectNode fn = tool.putObject("function");
                fn.put("name", t.name());
                if (t.description() != null && !t.description().isBlank()) {
                    fn.put("description", t.description());
                }
                fn.set("parameters", normalizeSchema(t.inputSchema()));
            }
        }

        ArrayNode messages = root.putArray("messages");
        if (req.system() != null && !req.system().isBlank()) {
            ObjectNode sys = messages.addObject();
            sys.put("role", "system");
            sys.put("content", req.system());
        }
        for (InputMessage m : req.messages()) {
            appendMessage(messages, m);
        }
        return root;
    }

    private void appendMessage(ArrayNode messages, InputMessage m) {
        List<ContentBlock> blocks = m.content();
        List<ContentBlock.ToolResult> toolResults = new java.util.ArrayList<>();
        List<ContentBlock.ToolUse> toolUses = new java.util.ArrayList<>();
        StringBuilder text = new StringBuilder();
        for (ContentBlock b : blocks) {
            switch (b) {
                case ContentBlock.Text t -> text.append(t.text());
                case ContentBlock.ToolUse use -> toolUses.add(use);
                case ContentBlock.ToolResult r -> toolResults.add(r);
                case ContentBlock.Thinking ignored -> {}
                case ContentBlock.RedactedThinking ignored -> {}
            }
        }

        if (!toolResults.isEmpty()) {
            for (ContentBlock.ToolResult r : toolResults) {
                ObjectNode tool = messages.addObject();
                tool.put("role", "tool");
                tool.put("tool_call_id", r.toolUseId());
                tool.put("content", flattenToolResult(r));
            }
            return;
        }

        if ("assistant".equals(m.role()) && !toolUses.isEmpty()) {
            ObjectNode obj = messages.addObject();
            obj.put("role", "assistant");
            if (text.length() > 0) {
                obj.put("content", text.toString());
            } else {
                obj.putNull("content");
            }
            ArrayNode calls = obj.putArray("tool_calls");
            for (ContentBlock.ToolUse use : toolUses) {
                ObjectNode call = calls.addObject();
                call.put("id", use.id());
                call.put("type", "function");
                ObjectNode fn = call.putObject("function");
                fn.put("name", use.name());
                fn.put("arguments", serializeArgs(use.input()));
            }
            return;
        }

        if (text.length() == 0) return;
        ObjectNode obj = messages.addObject();
        obj.put("role", m.role());
        obj.put("content", text.toString());
    }

    private String serializeArgs(JsonNode input) {
        try {
            if (input == null || input.isNull()) return "{}";
            return mapper.writeValueAsString(input);
        } catch (JsonProcessingException e) {
            throw new ApiException("Failed to serialize tool arguments", e);
        }
    }

    private String flattenToolResult(ContentBlock.ToolResult r) {
        StringBuilder sb = new StringBuilder();
        for (ToolResultContentBlock part : r.content()) {
            switch (part) {
                case ToolResultContentBlock.Text t -> sb.append(t.text());
                case ToolResultContentBlock.Json j -> {
                    try {
                        sb.append(mapper.writeValueAsString(j.value()));
                    } catch (JsonProcessingException e) {
                        throw new ApiException("Failed to serialize tool_result json", e);
                    }
                }
            }
        }
        return sb.toString();
    }

    private JsonNode normalizeSchema(JsonNode schema) {
        if (schema == null || schema.isNull() || !schema.isObject()) {
            ObjectNode empty = mapper.createObjectNode();
            empty.put("type", "object");
            empty.putObject("properties");
            return empty;
        }
        return schema;
    }

    static final class OpenAiStreamTranslator {
        private static final String HERMES_FN_PREFIX = "<function=";
        private static final String HERMES_WRAPPER_PREFIX = "<tool_call>";

        private final ObjectMapper mapper;
        private final StreamEventHandler handler;
        private final String model;
        private final StringBuilder dataBuffer = new StringBuilder();
        private boolean messageStarted;
        private boolean textBlockStarted;
        private int nextContentIndex;
        private final Map<Integer, PendingToolCall> pendingToolCalls = new LinkedHashMap<>();
        private String finishReason;
        private int outputTokens;

        private final StringBuilder pendingText = new StringBuilder();
        private boolean textDecisionMade;
        private boolean hermesMode;

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
                    onContentDelta(contentNode.asText());
                }

                JsonNode toolCalls = delta.path("tool_calls");
                if (toolCalls.isArray()) {
                    for (JsonNode tc : toolCalls) {
                        int idx = tc.path("index").asInt(0);
                        PendingToolCall p = pendingToolCalls
                            .computeIfAbsent(idx, i -> new PendingToolCall());
                        if (tc.hasNonNull("id")) p.id = tc.get("id").asText();
                        JsonNode fn = tc.path("function");
                        if (fn.hasNonNull("name")) p.name = fn.get("name").asText();
                        if (fn.hasNonNull("arguments")) {
                            p.arguments.append(fn.get("arguments").asText());
                        }
                    }
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

        private void onContentDelta(String chunk) {
            pendingText.append(chunk);

            if (!textDecisionMade) {
                decidePendingText();
                if (!textDecisionMade) return;
            }
            if (hermesMode) return;

            if (!textBlockStarted) {
                handler.onEvent(new StreamEvent.ContentBlockStart(
                    nextContentIndex, new ContentBlock.Text("")));
                textBlockStarted = true;
            }
            String toFlush = pendingText.toString();
            pendingText.setLength(0);
            handler.onEvent(new StreamEvent.ContentBlockDeltaEvent(
                nextContentIndex,
                new ContentBlockDelta.TextDelta(toFlush)));
        }

        private void decidePendingText() {
            int i = 0;
            while (i < pendingText.length() && Character.isWhitespace(pendingText.charAt(i))) i++;
            if (i == pendingText.length()) return;
            String trimmedHead = pendingText.substring(i);

            if (startsWith(trimmedHead, HERMES_FN_PREFIX)
                || startsWith(trimmedHead, HERMES_WRAPPER_PREFIX)) {
                textDecisionMade = true;
                hermesMode = true;
                return;
            }
            if (isPrefixOf(trimmedHead, HERMES_FN_PREFIX)
                || isPrefixOf(trimmedHead, HERMES_WRAPPER_PREFIX)) {
                return;
            }
            textDecisionMade = true;
            hermesMode = false;
        }

        private static boolean startsWith(String s, String target) {
            return s.length() >= target.length() && s.regionMatches(0, target, 0, target.length());
        }

        private static boolean isPrefixOf(String partial, String full) {
            return partial.length() < full.length()
                && full.regionMatches(0, partial, 0, partial.length());
        }

        private void finish() {
            if (!messageStarted) return;

            if (hermesMode && pendingText.length() > 0) {
                extractHermesToolCalls(pendingText.toString());
                pendingText.setLength(0);
            } else if (pendingText.length() > 0) {
                if (!textBlockStarted) {
                    handler.onEvent(new StreamEvent.ContentBlockStart(
                        nextContentIndex, new ContentBlock.Text("")));
                    textBlockStarted = true;
                }
                handler.onEvent(new StreamEvent.ContentBlockDeltaEvent(
                    nextContentIndex,
                    new ContentBlockDelta.TextDelta(pendingText.toString())));
                pendingText.setLength(0);
            }

            if (textBlockStarted) {
                handler.onEvent(new StreamEvent.ContentBlockStop(nextContentIndex));
                textBlockStarted = false;
                nextContentIndex++;
            }

            boolean hermesCalled = hermesMode && !pendingToolCalls.isEmpty();

            int counter = 0;
            for (PendingToolCall p : pendingToolCalls.values()) {
                String id = p.id != null ? p.id : "call_" + counter;
                String name = p.name != null ? p.name : "";
                handler.onEvent(new StreamEvent.ContentBlockStart(
                    nextContentIndex,
                    new ContentBlock.ToolUse(id, name, mapper.createObjectNode())));
                String args = p.arguments.length() == 0 ? "{}" : p.arguments.toString();
                handler.onEvent(new StreamEvent.ContentBlockDeltaEvent(
                    nextContentIndex,
                    new ContentBlockDelta.InputJsonDelta(args)));
                handler.onEvent(new StreamEvent.ContentBlockStop(nextContentIndex));
                nextContentIndex++;
                counter++;
            }
            pendingToolCalls.clear();

            String stop = hermesCalled
                ? "tool_use"
                : (finishReason == null ? "end_turn" : translateFinish(finishReason));
            handler.onEvent(new StreamEvent.MessageDeltaEvent(
                new StreamEvent.MessageDelta(stop, null),
                new Usage(0, 0, 0, outputTokens)));
            handler.onEvent(StreamEvent.MessageStop.INSTANCE);
            messageStarted = false;
            finishReason = null;
            outputTokens = 0;
            nextContentIndex = 0;
            textDecisionMade = false;
            hermesMode = false;
        }

        private void extractHermesToolCalls(String text) {
            java.util.regex.Matcher fn = java.util.regex.Pattern
                .compile("<function=([^>\\s]+)>([\\s\\S]*?)</function>")
                .matcher(text);
            int counter = 0;
            while (fn.find()) {
                String name = fn.group(1);
                String body = fn.group(2);
                ObjectNode args = mapper.createObjectNode();
                java.util.regex.Matcher params = java.util.regex.Pattern
                    .compile("<parameter=([^>\\s]+)>([\\s\\S]*?)</parameter>")
                    .matcher(body);
                while (params.find()) {
                    String key = params.group(1);
                    String raw = params.group(2).strip();
                    putHermesArg(args, key, raw);
                }
                PendingToolCall p = new PendingToolCall();
                p.id = "call_hermes_" + counter++;
                p.name = name;
                try {
                    p.arguments.append(mapper.writeValueAsString(args));
                } catch (JsonProcessingException e) {
                    throw new ApiException("Failed to serialize Hermes tool arguments", e);
                }
                pendingToolCalls.put(pendingToolCalls.size(), p);
            }
        }

        private void putHermesArg(ObjectNode args, String key, String raw) {
            try {
                JsonNode parsed = mapper.readTree(raw);
                if (parsed.isNumber() || parsed.isBoolean() || parsed.isObject() || parsed.isArray()) {
                    args.set(key, parsed);
                    return;
                }
            } catch (IOException ignored) {
                // fall through to string
            }
            args.put(key, raw);
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

        private static final class PendingToolCall {
            String id;
            String name;
            final StringBuilder arguments = new StringBuilder();
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
