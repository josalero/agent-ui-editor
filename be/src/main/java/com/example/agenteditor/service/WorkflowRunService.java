package com.example.agenteditor.service;

import com.example.agenteditor.api.WorkflowNotFoundException;
import com.example.agenteditor.api.v1.dto.RunWorkflowResponse;
import com.example.agenteditor.api.v1.dto.WorkflowNodeDto;
import com.example.agenteditor.interpreter.WorkflowGraphInterpreter;
import com.example.agenteditor.interpreter.WorkflowRunnable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        Map<String, Object> runInput = input != null ? input : Map.of();
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
        Object result = runnable.run(runInput);
        String resultStr = result != null ? result.toString() : "";
        if (resultStr.isBlank()) {
            String fallback = fallbackResultForEmptyOutput(entryNodeId, nodes, runInput);
            if (!fallback.isBlank()) {
                resultStr = fallback;
            }
        }
        log.info("Workflow run completed id={} resultLength={}", workflowId, resultStr.length());
        return new RunWorkflowResponse(resultStr);
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
