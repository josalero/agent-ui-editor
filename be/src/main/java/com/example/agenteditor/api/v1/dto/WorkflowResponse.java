package com.example.agenteditor.api.v1.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full workflow graph response (get by id, update).
 */
public record WorkflowResponse(
        UUID id,
        String name,
        String entryNodeId,
        List<WorkflowNodeDto> nodes,
        Instant createdAt,
        Instant updatedAt
) {}
