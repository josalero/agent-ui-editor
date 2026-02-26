package com.example.agenteditor.service;

import com.example.agenteditor.api.WorkflowNotFoundException;
import com.example.agenteditor.api.v1.dto.WorkflowCreateRequest;
import com.example.agenteditor.api.v1.dto.WorkflowListItem;
import com.example.agenteditor.api.v1.dto.WorkflowNodeDto;
import com.example.agenteditor.api.v1.dto.WorkflowResponse;
import com.example.agenteditor.api.v1.dto.WorkflowUpdateRequest;
import com.example.agenteditor.domain.WorkflowDefinition;
import com.example.agenteditor.repository.WorkflowDefinitionRepository;
import com.example.agenteditor.validation.WorkflowGraphValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static java.util.Collections.unmodifiableList;

/**
 * Application service for workflow definition CRUD and graph validation.
 * <p>
 * Validates workflow graphs via {@link WorkflowGraphValidator} before create/update,
 * persists the graph as JSON in {@link WorkflowDefinition#getGraphJson()}, and maps
 * entities to/from DTOs.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowDefinitionService {

    private final WorkflowDefinitionRepository repository;
    private final JsonMapper jsonMapper;

    @Transactional
    public UUID create(WorkflowCreateRequest request) {
        log.debug("Validating and persisting new workflow name={} nodes={}", request.name(), request.nodes() != null ? request.nodes().size() : 0);
        WorkflowGraphValidator.validate(request.entryNodeId(), request.nodes());
        String graphJson = writeNodesAsJson(request.nodes());
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        WorkflowDefinition entity = new WorkflowDefinition(
                id,
                request.name(),
                request.entryNodeId(),
                graphJson,
                now,
                now
        );
        repository.save(entity);
        log.debug("Persisted workflow id={} entryNodeId={}", id, request.entryNodeId());
        return id;
    }

    /** Names of example workflows loaded from classpath at startup. */
    public static final List<String> EXAMPLE_WORKFLOW_NAMES = unmodifiableList(List.of(
            "Story workflow",
            "Evening plan workflow",
            "Expert router workflow",
            "Supervisor workflow"
    ));

    @Transactional(readOnly = true)
    public List<WorkflowListItem> findAll() {
        List<WorkflowListItem> list = repository.findAll().stream()
                .map(this::toListItem)
                .toList();
        log.debug("findAll returned {} workflows", list.size());
        return list;
    }

    @Transactional(readOnly = true)
    public List<WorkflowListItem> findSamples() {
        List<WorkflowListItem> list = repository.findByNameIn(EXAMPLE_WORKFLOW_NAMES).stream()
                .map(this::toListItem)
                .toList();
        log.debug("findSamples returned {} workflows", list.size());
        return list;
    }

    @Transactional(readOnly = true)
    public WorkflowResponse findById(UUID id) {
        log.debug("Finding workflow by id={}", id);
        WorkflowDefinition entity = repository.findById(id)
                .orElseThrow(() -> new WorkflowNotFoundException(id));
        return toResponse(entity);
    }

    @Transactional
    public WorkflowResponse update(UUID id, WorkflowUpdateRequest request) {
        log.debug("Updating workflow id={} name={}", id, request.name());
        WorkflowDefinition existing = repository.findById(id)
                .orElseThrow(() -> new WorkflowNotFoundException(id));
        WorkflowGraphValidator.validate(request.entryNodeId(), request.nodes());
        String graphJson = writeNodesAsJson(request.nodes());
        Instant now = Instant.now();
        WorkflowDefinition updated = new WorkflowDefinition(
                existing.getId(),
                request.name(),
                request.entryNodeId(),
                graphJson,
                existing.getCreatedAt(),
                now
        );
        repository.save(updated);
        log.debug("Updated workflow id={}", id);
        return toResponse(updated);
    }

    @Transactional
    public void delete(UUID id) {
        log.debug("Deleting workflow id={}", id);
        if (!repository.existsById(id)) {
            throw new WorkflowNotFoundException(id);
        }
        repository.deleteById(id);
    }

    private WorkflowListItem toListItem(WorkflowDefinition entity) {
        return new WorkflowListItem(entity.getId(), entity.getName(), entity.getUpdatedAt());
    }

    private WorkflowResponse toResponse(WorkflowDefinition entity) {
        List<WorkflowNodeDto> nodes = readNodesFromJson(entity.getGraphJson());
        return new WorkflowResponse(
                entity.getId(),
                entity.getName(),
                entity.getEntryNodeId(),
                nodes,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String writeNodesAsJson(List<WorkflowNodeDto> nodes) {
        try {
            return jsonMapper.writeValueAsString(nodes);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize workflow nodes", e);
        }
    }

    private List<WorkflowNodeDto> readNodesFromJson(String graphJson) {
        try {
            return jsonMapper.readValue(graphJson, new TypeReference<List<WorkflowNodeDto>>() { });
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to deserialize workflow nodes", e);
        }
    }
}
