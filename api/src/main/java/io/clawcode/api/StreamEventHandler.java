package io.clawcode.api;

@FunctionalInterface
public interface StreamEventHandler {
    void onEvent(StreamEvent event);
}
