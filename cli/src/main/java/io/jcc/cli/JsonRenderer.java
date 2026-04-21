package io.jcc.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jcc.core.JsonMapper;

import java.io.PrintStream;

public final class JsonRenderer implements StreamingRenderer {

    private final PrintStream out;
    private final ObjectMapper mapper;

    public JsonRenderer(PrintStream out) {
        this(out, JsonMapper.shared());
    }

    JsonRenderer(PrintStream out, ObjectMapper mapper) {
        this.out = out;
        this.mapper = mapper;
    }

    @Override
    public void onEvent(AssistantEvent event) {
        try {
            out.println(mapper.writeValueAsString(event));
            out.flush();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize assistant event", e);
        }
    }

    @Override
    public void close() {
        out.flush();
    }
}
