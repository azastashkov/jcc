package io.clawcode.api;

public interface ProviderClient {
    void stream(MessageRequest request, StreamEventHandler handler);
}
