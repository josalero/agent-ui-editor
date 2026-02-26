package com.example.agenteditor.validation;

import java.util.Objects;

/**
 * A single validation error (field and message).
 */
public record ValidationError(String field, String message) {
    public ValidationError {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(message, "message");
    }
}
