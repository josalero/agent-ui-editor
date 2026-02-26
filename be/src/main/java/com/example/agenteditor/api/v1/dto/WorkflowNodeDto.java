package com.example.agenteditor.api.v1.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;

/**
 * One node in a workflow graph. Type-specific fields are optional;
 * validator enforces required fields per node type.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowNodeDto(
        @NotBlank String id,
        @NotNull String type,
        String baseUrl,
        String modelName,
        String llmId,
        String name,
        String role,
        String systemMessage,
        String promptTemplate,
        String outputKey,
        List<String> toolIds,
        List<String> subAgentIds,
        String responseStrategy,
        String routerAgentId,
        List<ConditionalBranchDto> branches,
        Integer threadPoolSize
) {
    public WorkflowNodeDto {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
    }
}
