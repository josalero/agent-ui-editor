package com.example.agenteditor.service;

import com.example.agenteditor.api.WorkflowNotFoundException;
import com.example.agenteditor.api.v1.dto.RunWorkflowResponse;
import com.example.agenteditor.api.v1.dto.WorkflowNodeDto;
import com.example.agenteditor.interpreter.WorkflowGraphInterpreter;
import com.example.agenteditor.interpreter.WorkflowRunnable;
import dev.langchain4j.agentic.scope.AgentInvocation;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runs a workflow by id: loads graph, builds entry runnable, invokes with input.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowRunService {

    private final WorkflowDefinitionService workflowDefinitionService;
    private final WorkflowGraphInterpreter interpreter;

    /**
     * Runs the workflow with the given id using input as scope. Returns the result string.
     *
     * @throws WorkflowNotFoundException if the workflow does not exist
     * @throws IllegalArgumentException  if the graph cannot be interpreted (invalid entry, missing refs, etc.)
     */
    public RunWorkflowResponse run(UUID workflowId, Map<String, Object> input) {
        var response = workflowDefinitionService.findById(workflowId);
        Map<String, Object> runInput = input != null ? new HashMap<>(input) : new HashMap<>();
        log.info("Run workflow id={} name={} entryNodeId={} inputKeys={}", workflowId, response.name(), response.entryNodeId(), runInput.keySet());
        Object metadata = runInput.get("metadata");
        if (metadata instanceof Map<?, ?> metadataMap) {
            log.info("Run input: hasMetadataKey=true metadataKeys={}", metadataMap.keySet());
        } else {
            log.info("Run input: hasMetadataKey={} metadataType={}", metadata != null, metadata != null ? metadata.getClass().getSimpleName() : "none");
        }
        List<WorkflowNodeDto> nodes = response.nodes();
        String entryNodeId = response.entryNodeId();
        log.debug("Building runnable entryNodeId={} nodeCount={}", entryNodeId, nodes != null ? nodes.size() : 0);
        WorkflowRunnable runnable = interpreter.buildEntryRunnable(entryNodeId, nodes);
        log.info("Executing workflow id={}", workflowId);
        Object execution = runnable.run(runInput);
        AgenticScope scope = executionScope(execution);
        if (scope != null && scope.state() != null && !scope.state().isEmpty()) {
            runInput.putAll(scope.state());
        }
        List<String> executedNodeIds = executedNodeIds(scope, nodes);
        List<String> executedNodeNames = executedNodeNames(executedNodeIds, nodes);
        Object result = executionResult(execution);
        String resultStr = result != null ? result.toString() : "";
        if (resultStr.isBlank()) {
            String fallback = fallbackResultForEmptyOutput(entryNodeId, nodes, runInput);
            if (!fallback.isBlank()) {
                resultStr = fallback;
            }
        }
        log.info("Workflow run completed id={} resultLength={} executedNodes={}", workflowId, resultStr.length(), executedNodeIds);
        return new RunWorkflowResponse(resultStr, executedNodeIds, executedNodeNames);
    }

    private Object executionResult(Object execution) {
        if (execution instanceof ResultWithAgenticScope<?> withScope) {
            return withScope.result();
        }
        return execution;
    }

    private AgenticScope executionScope(Object execution) {
        if (execution instanceof ResultWithAgenticScope<?> withScope) {
            return withScope.agenticScope();
        }
        return null;
    }

    private List<String> executedNodeIds(AgenticScope scope, List<WorkflowNodeDto> nodes) {
        if (scope == null || nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        List<AgentInvocation> invocations = scope.agentInvocations();
        if (invocations == null || invocations.isEmpty()) {
            return List.of();
        }
        Map<String, String> nameToId = new HashMap<>();
        Set<String> ids = new LinkedHashSet<>();
        for (WorkflowNodeDto node : nodes) {
            if (node.name() != null && !node.name().isBlank()) {
                nameToId.put(node.name(), node.id());
            }
        }
        for (AgentInvocation invocation : invocations) {
            String resolved = resolveNodeId(invocation, nodes, nameToId);
            if (resolved != null) {
                ids.add(resolved);
            }
        }
        return new ArrayList<>(ids);
    }

    private String resolveNodeId(AgentInvocation invocation, List<WorkflowNodeDto> nodes, Map<String, String> nameToId) {
        if (invocation == null) {
            return null;
        }
        String byId = matchNodeId(invocation.agentId(), nodes);
        if (byId != null) {
            return byId;
        }
        String byNameAsId = matchNodeId(invocation.agentName(), nodes);
        if (byNameAsId != null) {
            return byNameAsId;
        }
        if (invocation.agentName() != null && nameToId.containsKey(invocation.agentName())) {
            return nameToId.get(invocation.agentName());
        }
        if (invocation.agentId() != null && nameToId.containsKey(invocation.agentId())) {
            return nameToId.get(invocation.agentId());
        }
        return null;
    }

    private String matchNodeId(String candidate, List<WorkflowNodeDto> nodes) {
        if (candidate == null || candidate.isBlank() || nodes == null) {
            return null;
        }
        for (WorkflowNodeDto node : nodes) {
            if (candidate.equals(node.id())) {
                return node.id();
            }
        }
        return null;
    }

    private List<String> executedNodeNames(List<String> nodeIds, List<WorkflowNodeDto> nodes) {
        if (nodeIds == null || nodeIds.isEmpty() || nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        Map<String, String> names = new HashMap<>();
        for (WorkflowNodeDto node : nodes) {
            names.put(node.id(), node.name() != null && !node.name().isBlank() ? node.name() : node.id());
        }
        List<String> resolved = new ArrayList<>();
        for (String id : nodeIds) {
            if (id != null && names.containsKey(id)) {
                resolved.add(names.get(id));
            }
        }
        return resolved;
    }

    private String fallbackResultForEmptyOutput(String entryNodeId, List<WorkflowNodeDto> nodes, Map<String, Object> runInput) {
        if (nodes == null || nodes.isEmpty()) {
            return "";
        }
        WorkflowNodeDto entry = nodes.stream()
                .filter(n -> entryNodeId.equals(n.id()))
                .findFirst()
                .orElse(null);
        if (entry == null) {
            return "";
        }
        String outputKey = entry.outputKey();
        if (outputKey != null && !outputKey.isBlank() && runInput != null) {
            Object value = runInput.get(outputKey);
            if (value != null && !value.toString().isBlank()) {
                log.info("Run fallback: returning value from entry outputKey={} length={}", outputKey, value.toString().length());
                return value.toString();
            }
        }
        if ("parallel".equalsIgnoreCase(entry.type())) {
            List<String> availableKeys = runInput != null ? new ArrayList<>(runInput.keySet()) : List.of();
            String message = "Workflow completed, but parallel entry node '" + entry.id() + "' returned no direct text output."
                    + " Add a final agent in a sequence/supervisor to compose sub-agent outputs.";
            log.warn("Run fallback: {}", message + " availableInputKeys=" + availableKeys);
            return message;
        }
        return "";
    }
}
