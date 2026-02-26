package com.example.agenteditor.api;

import lombok.Getter;

import java.util.UUID;

/**
 * Thrown when a workflow is not found by id.
 * <p>
 * Mapped to HTTP 404 by {@link GlobalExceptionHandler}.
 * </p>
 */
@Getter
public class WorkflowNotFoundException extends RuntimeException {

    private final UUID workflowId;

    public WorkflowNotFoundException(UUID workflowId) {
        super("Workflow not found: " + workflowId);
        this.workflowId = workflowId;
    }
}
