package com.example.agenteditor.interpreter;

import com.example.agenteditor.api.v1.dto.WorkflowNodeDto;
import com.example.agenteditor.llm.StubChatModel;
import com.example.agenteditor.llm.StubOpenRouterChatModelFactory;
import com.example.agenteditor.interpreter.WorkflowRunnable;
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
                    new WorkflowNodeDto("llm-1", "llm", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini",
                            null, null, null, null, null, null, null, null, null),
                    new WorkflowNodeDto("writer", "agent", null, null, "llm-1", "CreativeWriter", "story",
                            null, null, null, null, null, null),
                    new WorkflowNodeDto("editor", "agent", null, null, "llm-1", "StyleEditor", "story",
                            null, null, null, null, null, null),
                    new WorkflowNodeDto("seq-story", "sequence", null, null, null, null, "story",
                            null, List.of("writer", "editor"), null, null, null, null)
            );
            WorkflowRunnable runnable = interpreter.buildEntryRunnable("seq-story", nodes);
            assertNotNull(runnable);
        }

        @Test
        @DisplayName("agent with toolIds receives tools from registry")
        void agentWithToolIdsResolvesTools() {
            List<WorkflowNodeDto> nodes = List.of(
                    new WorkflowNodeDto("llm-1", "llm", null, null, null, null, null, null, null, null, null, null, null),
                    new WorkflowNodeDto("general", "agent", null, null, "llm-1", "GeneralExpert", "response",
                            List.of("time", "calculator"), null, null, null, null, null)
            );
            WorkflowRunnable runnable = interpreter.buildEntryRunnable("general", nodes);
            assertNotNull(runnable);
        }

        @Test
        @DisplayName("throws when entry node is not agent or sequence")
        void throwsWhenEntryIsLlm() {
            List<WorkflowNodeDto> nodes = List.of(
                    new WorkflowNodeDto("llm-1", "llm", null, null, null, null, null, null, null, null, null, null, null)
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
                    new WorkflowNodeDto("llm-1", "llm", null, null, null, null, null, null, null, null, null, null, null),
                    new WorkflowNodeDto("agent1", "agent", null, null, "llm-1", "A", null, null, null, null, null, null, null)
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
}
