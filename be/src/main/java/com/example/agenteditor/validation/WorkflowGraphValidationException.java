package com.example.agenteditor.validation;

import lombok.Getter;

import java.util.List;

/**
 * Thrown when workflow graph validation fails (e.g. missing refs, invalid node type).
 * <p>
 * Mapped to HTTP 400 with {@link #getErrors()} in the response body by {@link com.example.agenteditor.api.GlobalExceptionHandler}.
 * </p>
 */
@Getter
public class WorkflowGraphValidationException extends RuntimeException {

    private final List<ValidationError> errors;

    public WorkflowGraphValidationException(List<ValidationError> errors) {
        super("Workflow graph validation failed: " + (errors != null ? errors.size() + " error(s)" : ""));
        this.errors = errors != null ? List.copyOf(errors) : List.of();
    }
}
