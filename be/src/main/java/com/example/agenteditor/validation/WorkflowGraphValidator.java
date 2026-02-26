package com.example.agenteditor.validation;

import com.example.agenteditor.api.v1.dto.ConditionalBranchDto;
import com.example.agenteditor.api.v1.dto.WorkflowNodeDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates a workflow graph: node types, required fields, and reference integrity.
 */
public final class WorkflowGraphValidator {

    private static final Set<String> VALID_TYPES = Set.of("llm", "agent", "supervisor", "sequence", "parallel", "conditional");
    private static final Set<String> VALID_ENTRY_TYPES = Set.of("sequence", "parallel", "supervisor");

    private WorkflowGraphValidator() {
    }

    /**
     * Validates the graph. Throws {@link WorkflowGraphValidationException} with all errors if invalid.
     */
    public static void validate(String entryNodeId, List<WorkflowNodeDto> nodes) {
        List<ValidationError> errors = new ArrayList<>();

        if (entryNodeId == null || entryNodeId.isBlank()) {
            errors.add(new ValidationError("entryNodeId", "entryNodeId is required"));
        }
        if (nodes == null || nodes.isEmpty()) {
            errors.add(new ValidationError("nodes", "at least one node is required"));
            throw new WorkflowGraphValidationException(errors);
        }

        Set<String> nodeIds = nodes.stream()
                .map(WorkflowNodeDto::id)
                .collect(Collectors.toSet());

        if (entryNodeId != null && !entryNodeId.isBlank() && !nodeIds.contains(entryNodeId)) {
            errors.add(new ValidationError("entryNodeId", "entryNodeId must reference an existing node id: " + entryNodeId));
        }
        if (entryNodeId != null && !entryNodeId.isBlank()) {
            WorkflowNodeDto entryNode = nodes.stream()
                    .filter(n -> entryNodeId.equals(n.id()))
                    .findFirst()
                    .orElse(null);
            if (entryNode != null && VALID_TYPES.contains(entryNode.type()) && !VALID_ENTRY_TYPES.contains(entryNode.type())) {
                errors.add(new ValidationError("entryNodeId", "entryNodeId must reference a node of type sequence, parallel, or supervisor"));
            }
        }

        for (WorkflowNodeDto node : nodes) {
            validateNode(node, nodeIds, errors);
        }

        if (!errors.isEmpty()) {
            throw new WorkflowGraphValidationException(errors);
        }
    }

    private static void validateNode(WorkflowNodeDto node, Set<String> nodeIds, List<ValidationError> errors) {
        String nodeId = node.id();
        String prefix = "nodes[" + nodeId + "]";

        if (node.id() == null || node.id().isBlank()) {
            errors.add(new ValidationError(prefix + ".id", "node id is required"));
        }
        if (node.type() == null || node.type().isBlank()) {
            errors.add(new ValidationError(prefix + ".type", "node type is required"));
        } else if (!VALID_TYPES.contains(node.type())) {
            errors.add(new ValidationError(prefix + ".type", "invalid type '" + node.type() + "'; must be one of: " + VALID_TYPES));
        }

        if (node.llmId() != null && !node.llmId().isBlank() && !nodeIds.contains(node.llmId())) {
            errors.add(new ValidationError(prefix + ".llmId", "llmId must reference an existing node id: " + node.llmId()));
        }
        if (node.subAgentIds() != null) {
            for (String refId : node.subAgentIds()) {
                if (refId != null && !refId.isBlank() && !nodeIds.contains(refId)) {
                    errors.add(new ValidationError(prefix + ".subAgentIds", "subAgentIds must reference existing node ids: " + refId));
                }
            }
        }
        if (node.routerAgentId() != null && !node.routerAgentId().isBlank() && !nodeIds.contains(node.routerAgentId())) {
            errors.add(new ValidationError(prefix + ".routerAgentId", "routerAgentId must reference an existing node id: " + node.routerAgentId()));
        }
        if (node.branches() != null) {
            for (ConditionalBranchDto branch : node.branches()) {
                if (branch.agentId() != null && !branch.agentId().isBlank() && !nodeIds.contains(branch.agentId())) {
                    errors.add(new ValidationError(prefix + ".branches", "branch agentId must reference an existing node id: " + branch.agentId()));
                }
            }
        }
    }
}
