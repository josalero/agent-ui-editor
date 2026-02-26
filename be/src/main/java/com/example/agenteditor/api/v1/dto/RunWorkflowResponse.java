package com.example.agenteditor.api.v1.dto;

import java.util.List;

/**
 * Response body for POST /api/v1/workflows/{id}/run.
 */
public record RunWorkflowResponse(
        String result,
        List<String> executedNodeIds,
        List<String> executedNodeNames
) {
    public RunWorkflowResponse(String result) {
        this(result, List.of(), List.of());
    }
}
