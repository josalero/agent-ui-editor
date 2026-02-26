package com.example.agenteditor.service;

import com.example.agenteditor.api.WorkflowNotFoundException;
import com.example.agenteditor.api.v1.dto.RunWorkflowResponse;
import com.example.agenteditor.api.v1.dto.WorkflowNodeDto;
import com.example.agenteditor.interpreter.WorkflowGraphInterpreter;
import com.example.agenteditor.interpreter.WorkflowRunnable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
        log.info("Workflow run completed id={} resultLength={}", workflowId, resultStr.length());
        return new RunWorkflowResponse(resultStr);
    }
}
