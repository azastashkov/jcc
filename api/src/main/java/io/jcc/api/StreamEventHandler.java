package io.jcc.api;

@FunctionalInterface
public interface StreamEventHandler {
    void onEvent(StreamEvent event);
}
