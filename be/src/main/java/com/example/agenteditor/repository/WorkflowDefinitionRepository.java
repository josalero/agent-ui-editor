package com.example.agenteditor.repository;

import com.example.agenteditor.domain.WorkflowDefinition;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, UUID> {

    Optional<WorkflowDefinition> findByName(String name);

    List<WorkflowDefinition> findByNameIn(List<String> names);
}
