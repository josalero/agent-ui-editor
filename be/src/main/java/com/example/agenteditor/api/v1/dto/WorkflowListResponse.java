package com.example.agenteditor.api.v1.dto;

import java.util.List;

/**
 * Response for GET /api/v1/workflows: list of workflow list items.
 */
public record WorkflowListResponse(List<WorkflowListItem> workflows) {}
