package com.example.agenteditor.interpreter;

import com.example.agenteditor.api.v1.dto.WorkflowNodeDto;
import com.example.agenteditor.llm.StubChatModel;
import com.example.agenteditor.llm.StubOpenRouterChatModelFactory;
import com.example.agenteditor.tools.DefaultToolRegistry;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.invocation.LangChain4jManaged;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("WorkflowGraphInterpreter")
class WorkflowGraphInterpreterTest {

    private WorkflowGraphInterpreter interpreter;

    @BeforeEach
    void setUp() {
        var factory = new StubOpenRouterChatModelFactory(new StubChatModel("stub reply"));
        interpreter = new WorkflowGraphInterpreter(factory, new DefaultToolRegistry());
    }

    @Nested
    @DisplayName("buildEntryRunnable")
    class BuildEntryRunnable {

        @Test
        @DisplayName("builds runnable for story-like graph (llm + 2 agents + sequence)")
        void storyGraphReturnsRunnable() {
            List<WorkflowNodeDto> nodes = List.of(
                    node("llm-1", "llm", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini",
                            null, null, null, null, null, null, null, null, null),
                    node("writer", "agent", null, null, "llm-1", "CreativeWriter", "story",
                            null, null, null, null, null, null),
                    node("editor", "agent", null, null, "llm-1", "StyleEditor", "story",
                            null, null, null, null, null, null),
                    node("seq-story", "sequence", null, null, null, null, "story",
                            null, List.of("writer", "editor"), null, null, null, null)
            );
            WorkflowRunnable runnable = interpreter.buildEntryRunnable("seq-story", nodes);
            assertNotNull(runnable);
        }

        @Test
        @DisplayName("agent with toolIds receives tools from registry")
        void agentWithToolIdsResolvesTools() {
            List<WorkflowNodeDto> nodes = List.of(
                    node("llm-1", "llm", null, null, null, null, null, null, null, null, null, null, null),
                    node("general", "agent", null, null, "llm-1", "GeneralExpert", "response",
                            List.of("time", "calculator"), null, null, null, null, null),
                    node("entry", "sequence", null, null, null, null, "response",
                            null, List.of("general"), null, null, null, null)
            );
            WorkflowRunnable runnable = interpreter.buildEntryRunnable("entry", nodes);
            assertNotNull(runnable);
        }

        @Test
        @DisplayName("parallel entry returns non-null output")
        void parallelEntryReturnsNonNullOutput() {
            List<WorkflowNodeDto> nodes = List.of(
                    node("llm-1", "llm", null, null, null, null, null, null, null, null, null, null, null),
                    node("movies", "agent", null, null, "llm-1", "MovieExpert", "movies",
                            null, null, null, null, null, null),
                    node("meals", "agent", null, null, "llm-1", "MealExpert", "meals",
                            null, null, null, null, null, null),
                    node("parallel-plan", "parallel", null, null, null, null, "plan",
                            null, List.of("movies", "meals"), null, null, null, 2)
            );
            WorkflowRunnable runnable = interpreter.buildEntryRunnable("parallel-plan", nodes);
            Object result = runnable.run(Map.of("metadata", Map.of("prompt", "Suggest an evening plan", "mood", "cozy")));
            assertNotNull(result);
        }

        @Test
        @DisplayName("throws when entry node type is not allowed")
        void throwsWhenEntryIsLlm() {
            List<WorkflowNodeDto> nodes = List.of(
                    node("llm-1", "llm", null, null, null, null, null, null, null, null, null, null, null)
            );
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> interpreter.buildEntryRunnable("llm-1", nodes));
            assertNotNull(ex.getMessage());
            assertTrue(ex.getMessage().contains("llm") && ex.getMessage().contains("Entry node"));
        }

        @Test
        @DisplayName("throws when entry node not found")
        void throwsWhenEntryNotFound() {
            List<WorkflowNodeDto> nodes = List.of(
                    node("llm-1", "llm", null, null, null, null, null, null, null, null, null, null, null),
                    node("agent1", "agent", null, null, "llm-1", "A", null, null, null, null, null, null, null)
            );
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> interpreter.buildEntryRunnable("missing", nodes));
            assertEquals("Entry node not found: missing", ex.getMessage());
        }

        @Test
        @DisplayName("resolves prompt from metadata in current managed scope when provider arg is default")
        void resolvesPromptFromMetadataInManagedScopeWhenProviderArgIsDefault() throws Exception {
            Method method = WorkflowGraphInterpreter.class.getDeclaredMethod("userMessageFromScope", Object.class);
            method.setAccessible(true);
            AgenticScope scope = agenticScope(Map.of("metadata", Map.of("prompt", "Write a short story about oceans.")));
            LangChain4jManaged.setCurrent(Map.of(AgenticScope.class, scope));
            try {
                String resolved = (String) method.invoke(interpreter, "default");
                assertEquals("Write a short story about oceans.", resolved);
            } finally {
                LangChain4jManaged.removeCurrent();
            }
        }

        @Test
        @DisplayName("resolves prompt from promptTemplate placeholders")
        void resolvesPromptFromTemplatePlaceholders() throws Exception {
            Method method = WorkflowGraphInterpreter.class.getDeclaredMethod("userMessageFromScope", Object.class, String.class);
            method.setAccessible(true);
            String template = "Task: {{metadata.prompt}}\nTopic: {{metadata.topic}}\nStyle: {{metadata.style}}";
            String resolved = (String) method.invoke(
                    interpreter,
                    Map.of("metadata", Map.of("prompt", "Write a short story.", "topic", "robot in Paris", "style", "noir")),
                    template
            );
            assertEquals("Task: Write a short story.\nTopic: robot in Paris\nStyle: noir", resolved);
        }

        @Test
        @DisplayName("falls back to metadata prompt when template variables are missing")
        void fallsBackToMetadataPromptWhenTemplateVariablesMissing() throws Exception {
            Method method = WorkflowGraphInterpreter.class.getDeclaredMethod("userMessageFromScope", Object.class, String.class);
            method.setAccessible(true);
            String resolved = (String) method.invoke(
                    interpreter,
                    Map.of("metadata", Map.of("prompt", "Use this fallback prompt.")),
                    "{{metadata.missing}}"
            );
            assertEquals("Use this fallback prompt.", resolved);
        }
    }

    private static AgenticScope agenticScope(Map<String, Object> state) {
        return (AgenticScope) Proxy.newProxyInstance(
                AgenticScope.class.getClassLoader(),
                new Class<?>[]{AgenticScope.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "state" -> state;
                    case "memoryId" -> "test-memory";
                    case "readState" -> {
                        if (args == null || args.length == 0) {
                            yield null;
                        }
                        Object value = state.get(args[0]);
                        if (args.length > 1 && value == null) {
                            yield args[1];
                        }
                        yield value;
                    }
                    case "hasState" -> args != null && args.length > 0 && state.containsKey(args[0]);
                    case "agentInvocations" -> List.of();
                    case "contextAsConversation" -> "";
                    case "writeState", "writeStates" -> null;
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == double.class) {
            return 0D;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return null;
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
            List<com.example.agenteditor.api.v1.dto.ConditionalBranchDto> branches,
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
