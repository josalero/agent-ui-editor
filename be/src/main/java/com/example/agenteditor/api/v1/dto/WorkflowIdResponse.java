package com.example.agenteditor.api.v1.dto;

import java.util.UUID;

/**
 * Response after creating a workflow (201): only the id.
 */
public record WorkflowIdResponse(UUID id) {}
