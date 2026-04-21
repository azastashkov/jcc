package io.clawcode.cli;

public interface StreamingRenderer extends AutoCloseable {

    void onEvent(AssistantEvent event);

    @Override
    void close();
}
