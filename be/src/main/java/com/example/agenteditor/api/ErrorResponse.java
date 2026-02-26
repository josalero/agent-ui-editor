package com.example.agenteditor.api;

import com.example.agenteditor.validation.ValidationError;

import java.util.List;

/**
 * Standard error response body (4xx/5xx): message and optional field errors.
 */
public record ErrorResponse(String message, List<ValidationError> errors) {

    public ErrorResponse(String message) {
        this(message, null);
    }

    public static ErrorResponse withErrors(String message, List<ValidationError> errors) {
        return new ErrorResponse(message, errors != null ? List.copyOf(errors) : null);
    }
}
