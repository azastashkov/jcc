package io.jcc.api;

public interface ProviderClient {
    void stream(MessageRequest request, StreamEventHandler handler);
}
