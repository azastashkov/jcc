package io.jcc.api;

public class ProviderException extends ApiException {

    private static final long serialVersionUID = 1L;

    public ProviderException(String message) {
        super(message);
    }

    public ProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
