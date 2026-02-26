package com.example.agenteditor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity for a persisted workflow definition.
 * <p>
 * Stores the workflow name, entry node id, and the graph (nodes) as JSON in {@code graph_json}.
 * Timestamps are set on create and update.
 * </p>
 */
@Entity
@Table(name = "workflow_definition")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkflowDefinition {

    @Id
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "entry_node_id", nullable = false, length = 255)
    private String entryNodeId;

    @Column(name = "graph_json", nullable = false, columnDefinition = "CLOB")
    private String graphJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public WorkflowDefinition(UUID id, String name, String entryNodeId, String graphJson, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.entryNodeId = Objects.requireNonNull(entryNodeId, "entryNodeId");
        this.graphJson = Objects.requireNonNull(graphJson, "graphJson");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
