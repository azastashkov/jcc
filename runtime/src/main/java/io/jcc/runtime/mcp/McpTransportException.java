package io.jcc.runtime.mcp;

public class McpTransportException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public McpTransportException(String message) {
        super(message);
    }

    public McpTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
