package com.example.agenteditor.api.v1.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Objects;

/**
 * One branch of a conditional node: condition key, value, and target agent id.
 */
public record ConditionalBranchDto(
        @NotBlank String conditionKey,
        @NotBlank String value,
        @NotBlank String agentId
) {
    public ConditionalBranchDto {
        Objects.requireNonNull(conditionKey, "conditionKey");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(agentId, "agentId");
    }
}
