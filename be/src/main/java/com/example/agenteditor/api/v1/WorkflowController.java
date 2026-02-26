package com.example.agenteditor.api.v1;

import com.example.agenteditor.api.v1.dto.RunWorkflowResponse;
import com.example.agenteditor.api.v1.dto.WorkflowCreateRequest;
import com.example.agenteditor.api.v1.dto.WorkflowIdResponse;
import com.example.agenteditor.api.v1.dto.WorkflowListResponse;
import com.example.agenteditor.api.v1.dto.WorkflowResponse;
import com.example.agenteditor.api.v1.dto.WorkflowUpdateRequest;
import com.example.agenteditor.service.WorkflowDefinitionService;
import com.example.agenteditor.service.WorkflowRunService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * REST controller for workflow CRUD operations.
 * <p>
 * Exposes {@code /api/v1/workflows} for create (POST), list (GET), get by id (GET /{id}),
 * update (PUT /{id}), and delete (DELETE /{id}). Request/response bodies use DTOs; create and
 * update are validated with {@code @Valid}.
 * </p>
 */
@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
@Slf4j
public class WorkflowController {

    private final WorkflowDefinitionService service;
    private final WorkflowRunService runService;

    @PostMapping
    public ResponseEntity<WorkflowIdResponse> create(@Valid @RequestBody WorkflowCreateRequest request) {
        log.info("Creating workflow name={} entryNodeId={} nodeCount={}", request.name(), request.entryNodeId(), request.nodes() != null ? request.nodes().size() : 0);
        UUID id = service.create(request);
        log.info("Created workflow id={} name={}", id, request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(new WorkflowIdResponse(id));
    }

    @GetMapping
    public ResponseEntity<WorkflowListResponse> list() {
        log.debug("Listing all workflows");
        return ResponseEntity.ok(new WorkflowListResponse(service.findAll()));
    }

    @GetMapping("/samples")
    public ResponseEntity<WorkflowListResponse> samples() {
        log.debug("Listing sample workflows");
        return ResponseEntity.ok(new WorkflowListResponse(service.findSamples()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkflowResponse> getById(@PathVariable UUID id) {
        log.info("Getting workflow id={}", id);
        return ResponseEntity.ok(service.findById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WorkflowResponse> update(@PathVariable UUID id, @Valid @RequestBody WorkflowUpdateRequest request) {
        log.info("Updating workflow id={} name={} nodeCount={}", id, request.name(), request.nodes() != null ? request.nodes().size() : 0);
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        log.info("Deleting workflow id={}", id);
        service.delete(id);
        log.info("Deleted workflow id={}", id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/run")
    public ResponseEntity<RunWorkflowResponse> run(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> input) {
        log.info("Running workflow id={} inputKeys={}", id, input != null ? input.keySet().size() : 0);
        return ResponseEntity.ok(runService.run(id, input != null ? input : Map.of()));
    }
}
