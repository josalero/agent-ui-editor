package com.example.agenteditor.validation;

import com.example.agenteditor.api.v1.dto.ConditionalBranchDto;
import com.example.agenteditor.api.v1.dto.WorkflowNodeDto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("WorkflowGraphValidator")
class WorkflowGraphValidatorTest {

    @Nested
    @DisplayName("valid graph")
    class ValidGraph {

        @Test
        @DisplayName("passes when entry node exists and types are valid")
        void validSingleNodePasses() {
            List<WorkflowNodeDto> nodes = List.of(
                    node("n1", "sequence", null, null, null, null, "out", null, null, null, null, null, null)
            );
            assertDoesNotThrow(() -> WorkflowGraphValidator.validate("n1", nodes));
        }

        @Test
        @DisplayName("passes when refs point to existing nodes")
        void validGraphWithRefsPasses() {
            List<WorkflowNodeDto> nodes = List.of(
                    node("entry", "sequence", null, null, null, null, null, null,
                            List.of("llm1", "agent1"), null, null, null, null),
                    node("llm1", "llm", null, null, null, null, null, null, null, null, null, null, null),
                    node("agent1", "agent", null, null, "llm1", null, null, null, null, null, null, null, null)
            );
            assertDoesNotThrow(() -> WorkflowGraphValidator.validate("entry", nodes));
        }

        @Test
        @DisplayName("passes when conditional branches reference existing nodes")
        void validConditionalBranchesPass() {
            List<WorkflowNodeDto> nodes = List.of(
                    node("entry", "sequence", null, null, null, null, "response", null, List.of("cond"), null, null, null, null),
                    node("cond", "conditional", null, null, null, null, null, null, null, null, null,
                            List.of(
                                    new ConditionalBranchDto("key", "a", "agentA"),
                                    new ConditionalBranchDto("key", "b", "agentB")
                            ), null),
                    node("agentA", "agent", null, null, null, null, null, null, null, null, null, null, null),
                    node("agentB", "agent", null, null, null, null, null, null, null, null, null, null, null)
            );
            assertDoesNotThrow(() -> WorkflowGraphValidator.validate("entry", nodes));
        }
    }

    @Nested
    @DisplayName("invalid graph")
    class InvalidGraph {

        @Test
        @DisplayName("fails when entryNodeId is blank")
        void blankEntryNodeIdFails() {
            List<WorkflowNodeDto> nodes = List.of(
                    node("n1", "llm", null, null, null, null, null, null, null, null, null, null, null)
            );
            WorkflowGraphValidationException ex = assertThrows(WorkflowGraphValidationException.class,
                    () -> WorkflowGraphValidator.validate(" ", nodes));
            assertEquals(1, ex.getErrors().size());
            assertEquals("entryNodeId", ex.getErrors().get(0).field());
        }

        @Test
        @DisplayName("fails when entryNodeId does not reference any node")
        void missingEntryNodeFails() {
            List<WorkflowNodeDto> nodes = List.of(
                    node("n1", "llm", null, null, null, null, null, null, null, null, null, null, null)
            );
            WorkflowGraphValidationException ex = assertThrows(WorkflowGraphValidationException.class,
                    () -> WorkflowGraphValidator.validate("missing", nodes));
            assertEquals(1, ex.getErrors().size());
            assertEquals("entryNodeId", ex.getErrors().get(0).field());
        }

        @Test
        @DisplayName("fails when node type is invalid")
        void invalidTypeFails() {
            List<WorkflowNodeDto> nodes = List.of(
                    node("n1", "invalid_type", null, null, null, null, null, null, null, null, null, null, null)
            );
            WorkflowGraphValidationException ex = assertThrows(WorkflowGraphValidationException.class,
                    () -> WorkflowGraphValidator.validate("n1", nodes));
            assertEquals(1, ex.getErrors().size());
            assertEquals("nodes[n1].type", ex.getErrors().get(0).field());
        }

        @Test
        @DisplayName("fails when llmId references non-existent node")
        void missingLlmIdRefFails() {
            List<WorkflowNodeDto> nodes = List.of(
                    node("entry", "sequence", null, null, null, null, "out", null, List.of("n1"), null, null, null, null),
                    node("n1", "agent", null, null, "nonExistent", null, null, null, null, null, null, null, null)
            );
            WorkflowGraphValidationException ex = assertThrows(WorkflowGraphValidationException.class,
                    () -> WorkflowGraphValidator.validate("entry", nodes));
            assertTrue(ex.getErrors().stream().anyMatch(err -> "nodes[n1].llmId".equals(err.field())));
        }

        @Test
        @DisplayName("fails when subAgentIds reference non-existent node")
        void missingSubAgentIdRefFails() {
            List<WorkflowNodeDto> nodes = List.of(
                    node("sup", "supervisor", null, null, null, null, null, null, List.of("ghost"), null, null, null, null)
            );
            WorkflowGraphValidationException ex = assertThrows(WorkflowGraphValidationException.class,
                    () -> WorkflowGraphValidator.validate("sup", nodes));
            assertEquals(1, ex.getErrors().size());
            assertEquals("nodes[sup].subAgentIds", ex.getErrors().get(0).field());
        }

        @Test
        @DisplayName("fails when branch agentId references non-existent node")
        void missingBranchAgentIdFails() {
            List<WorkflowNodeDto> nodes = List.of(
                    node("entry", "sequence", null, null, null, null, "response", null, List.of("cond"), null, null, null, null),
                    node("cond", "conditional", null, null, null, null, null, null, null, null, null,
                            List.of(new ConditionalBranchDto("k", "v", "missingAgent")), null)
            );
            WorkflowGraphValidationException ex = assertThrows(WorkflowGraphValidationException.class,
                    () -> WorkflowGraphValidator.validate("entry", nodes));
            assertTrue(ex.getErrors().stream().anyMatch(err -> "nodes[cond].branches".equals(err.field())));
        }

        @Test
        @DisplayName("fails when entry node type is not sequence, parallel, or supervisor")
        void invalidEntryNodeTypeFails() {
            List<WorkflowNodeDto> nodes = List.of(
                    node("agent-entry", "agent", null, null, null, null, null, null, null, null, null, null, null)
            );
            WorkflowGraphValidationException ex = assertThrows(WorkflowGraphValidationException.class,
                    () -> WorkflowGraphValidator.validate("agent-entry", nodes));
            assertTrue(ex.getErrors().stream().anyMatch(err -> "entryNodeId".equals(err.field())));
        }
    }

    private static WorkflowNodeDto node(
            String id,
            String type,
            String baseUrl,
            String modelName,
            String llmId,
            String name,
            String outputKey,
            List<String> toolIds,
            List<String> subAgentIds,
            String responseStrategy,
            String routerAgentId,
            List<ConditionalBranchDto> branches,
            Integer threadPoolSize
    ) {
        return new WorkflowNodeDto(
                id,
                type,
                baseUrl,
                modelName,
                null,
                null,
                llmId,
                name,
                null,
                null,
                null,
                outputKey,
                null,
                toolIds,
                subAgentIds,
                responseStrategy,
                routerAgentId,
                branches,
                threadPoolSize
        );
    }
}
