package com.example.agenteditor.api.v1.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Workflow list item (id, name, updatedAt).
 */
public record WorkflowListItem(
        UUID id,
        String name,
        Instant updatedAt
) {}
