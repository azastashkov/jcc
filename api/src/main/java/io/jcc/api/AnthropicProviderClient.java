package io.jcc.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jcc.core.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AnthropicProviderClient implements ProviderClient {

    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final URI baseUrl;

    public AnthropicProviderClient(String apiKey) {
        this(apiKey, URI.create(DEFAULT_BASE_URL), HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .version(HttpClient.Version.HTTP_2)
            .build(), JsonMapper.shared());
    }

    public AnthropicProviderClient(String apiKey, URI baseUrl, HttpClient httpClient, ObjectMapper mapper) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.httpClient = httpClient;
        this.mapper = mapper;
    }

    @Override
    public void stream(MessageRequest request, StreamEventHandler handler) {
        MessageRequest streaming = request.withStream(true);
        String body;
        try {
            body = mapper.writeValueAsString(streaming);
        } catch (JsonProcessingException e) {
            throw new ApiException("Failed to serialize request", e);
        }

        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(baseUrl.resolve("/v1/messages"))
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .header("anthropic-beta", "prompt-caching-2024-07-31")
            .header("content-type", "application/json")
            .header("accept", "text/event-stream")
            .timeout(Duration.ofMinutes(10))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        SseLineParser parser = new SseLineParser(mapper, handler);
        LineSubscriber subscriber = new LineSubscriber(parser);

        try {
            HttpResponse<Void> response = httpClient.send(httpRequest, BodyHandlers.fromLineSubscriber(subscriber));
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new ProviderException("Anthropic returned HTTP " + status);
            }
            parser.close();
            if (subscriber.failure() != null) {
                throw new ProviderException("Stream terminated with error", subscriber.failure());
            }
        } catch (IOException e) {
            throw new ProviderException("Request to Anthropic failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderException("Request interrupted", e);
        }
    }

    private static final class LineSubscriber implements Flow.Subscriber<String> {
        private final SseLineParser parser;
        private final AtomicBoolean done = new AtomicBoolean();
        private volatile Throwable failure;

        LineSubscriber(SseLineParser parser) {
            this.parser = parser;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(String line) {
            if (done.get()) return;
            parser.pushLine(line);
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
