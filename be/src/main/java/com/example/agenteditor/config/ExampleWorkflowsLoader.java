package com.example.agenteditor.config;

import com.example.agenteditor.api.v1.dto.WorkflowCreateRequest;
import com.example.agenteditor.repository.WorkflowDefinitionRepository;
import com.example.agenteditor.service.WorkflowDefinitionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Loads example workflow definitions from classpath resources into the database at startup.
 * Skips any example that already exists (by name) so re-runs are idempotent.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExampleWorkflowsLoader implements ApplicationRunner {

    private static final String EXAMPLES_DIR = "examples/";
    private static final List<String> EXAMPLE_FILES = List.of(
            "story-workflow.json",
            "evening-plan-workflow.json",
            "expert-router-workflow.json",
            "supervisor-workflow.json"
    );

    private final WorkflowDefinitionRepository repository;
    private final WorkflowDefinitionService service;
    private final JsonMapper jsonMapper;

    @Override
    public void run(ApplicationArguments args) {
        for (String filename : EXAMPLE_FILES) {
            loadExample(EXAMPLES_DIR + filename);
        }
    }

    private void loadExample(String path) {
        Resource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            log.warn("Example workflow resource not found: {}", path);
            return;
        }
        try (InputStream in = resource.getInputStream()) {
            WorkflowCreateRequest request = jsonMapper.readValue(in, WorkflowCreateRequest.class);
            if (repository.findByName(request.name()).isPresent()) {
                log.debug("Example workflow already present, skipping: {}", request.name());
                return;
            }
            service.create(request);
            log.info("Loaded example workflow: {}", request.name());
        } catch (JacksonException e) {
            log.error("Failed to parse example workflow {}: {}", path, e.getMessage());
        } catch (IOException e) {
            log.error("Failed to read example workflow {}: {}", path, e.getMessage());
        }
    }
}
