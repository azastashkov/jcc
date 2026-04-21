package io.jcc.cli;

public interface StreamingRenderer extends AutoCloseable {

    void onEvent(AssistantEvent event);

    @Override
    void close();
}
