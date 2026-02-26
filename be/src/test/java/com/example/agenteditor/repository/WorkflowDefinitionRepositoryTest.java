package com.example.agenteditor.repository;

import com.example.agenteditor.domain.WorkflowDefinition;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("WorkflowDefinitionRepository")
class WorkflowDefinitionRepositoryTest {

    @Autowired
    private WorkflowDefinitionRepository repository;

    @Test
    @DisplayName("saves and loads workflow by id")
    void saveAndLoadWorkflow() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String graphJson = "{\"nodes\":[{\"id\":\"n1\",\"type\":\"llm\"}]}";
        WorkflowDefinition entity = new WorkflowDefinition(id, "My Flow", "n1", graphJson, now, now);

        repository.save(entity);

        Optional<WorkflowDefinition> loaded = repository.findById(id);
        assertTrue(loaded.isPresent());
        assertEquals(id, loaded.get().getId());
        assertEquals("My Flow", loaded.get().getName());
        assertEquals("n1", loaded.get().getEntryNodeId());
        assertEquals(graphJson, loaded.get().getGraphJson());
        assertEquals(now, loaded.get().getCreatedAt());
        assertEquals(now, loaded.get().getUpdatedAt());
    }
}
