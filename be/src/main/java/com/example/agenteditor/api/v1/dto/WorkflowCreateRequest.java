package com.example.agenteditor.api.v1.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Request body for creating a workflow.
 */
public record WorkflowCreateRequest(
        @NotBlank String name,
        @NotBlank String entryNodeId,
        @NotNull @NotEmpty @Valid List<WorkflowNodeDto> nodes
) {}
