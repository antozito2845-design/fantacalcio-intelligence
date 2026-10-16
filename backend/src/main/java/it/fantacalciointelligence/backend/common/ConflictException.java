package it.fantacalciointelligence.backend.common;

public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }

    public ConflictException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}