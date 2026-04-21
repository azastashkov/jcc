package io.jcc.runtime.subagent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TaskStatus {
    CREATED("created"),
    RUNNING("running"),
    COMPLETED("completed"),
    FAILED("failed"),
    STOPPED("stopped");

    private final String wire;

    TaskStatus(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    @JsonCreator
    public static TaskStatus fromWire(String value) {
        for (TaskStatus s : values()) {
            if (s.wire.equals(value)) return s;
        }
        throw new IllegalArgumentException("Unknown task status: " + value);
    }
}
