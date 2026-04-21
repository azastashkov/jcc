package io.jcc.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SseLineParser {

    private static final Logger log = LoggerFactory.getLogger(SseLineParser.class);

    private final ObjectMapper mapper;
    private final StreamEventHandler handler;
    private final StringBuilder dataBuffer = new StringBuilder();

    public SseLineParser(ObjectMapper mapper, StreamEventHandler handler) {
        this.mapper = mapper;
        this.handler = handler;
    }

    public void pushLine(String line) {
        if (line == null) {
            return;
        }
        if (line.isEmpty()) {
            emitFrame();
            return;
        }
        if (line.startsWith(":")) {
            return;
        }
        if (line.startsWith("data:")) {
            String payload = line.substring(5);
            if (!payload.isEmpty() && payload.charAt(0) == ' ') {
                payload = payload.substring(1);
            }
            if (dataBuffer.length() > 0) {
                dataBuffer.append('\n');
            }
            dataBuffer.append(payload);
        }
    }

    public void close() {
        if (dataBuffer.length() > 0) {
            emitFrame();
        }
    }

    private void emitFrame() {
        if (dataBuffer.length() == 0) {
            return;
        }
        String json = dataBuffer.toString();
        dataBuffer.setLength(0);

        try {
            StreamEvent event = mapper.readValue(json, StreamEvent.class);
            handler.onEvent(event);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse SSE frame: {}", json, e);
            throw new ProviderException("Malformed SSE frame", e);
        }
    }
}
