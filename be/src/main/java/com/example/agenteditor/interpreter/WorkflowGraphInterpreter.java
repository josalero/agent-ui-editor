package com.example.agenteditor.interpreter;

import com.example.agenteditor.api.v1.dto.ConditionalBranchDto;
import com.example.agenteditor.api.v1.dto.WorkflowNodeDto;
import com.example.agenteditor.llm.OpenRouterChatModelFactory;
import com.example.agenteditor.tools.ToolRegistry;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.invocation.LangChain4jManaged;
import dev.langchain4j.model.chat.ChatModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Interprets a workflow graph (all node types) and builds a runnable for the entry node.
 */
public class WorkflowGraphInterpreter {

    private static final Logger log = LoggerFactory.getLogger(WorkflowGraphInterpreter.class);
    private static final List<String> VALID_ENTRY_TYPES = List.of("agent", "sequence", "parallel", "conditional", "supervisor");

    private final OpenRouterChatModelFactory chatModelFactory;
    private final ToolRegistry toolRegistry;

    public WorkflowGraphInterpreter(OpenRouterChatModelFactory chatModelFactory, ToolRegistry toolRegistry) {
        this.chatModelFactory = Objects.requireNonNull(chatModelFactory, "chatModelFactory");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
    }

    /**
     * Builds the runnable for the given graph. Entry node must be agent, sequence, parallel, conditional, or supervisor.
     */
    public WorkflowRunnable buildEntryRunnable(String entryNodeId, List<WorkflowNodeDto> nodes) {
        Objects.requireNonNull(entryNodeId, "entryNodeId");
        Objects.requireNonNull(nodes, "nodes");
        log.info("Building runnable entryNodeId={} nodeCount={}", entryNodeId, nodes.size());
        Map<String, WorkflowNodeDto> byId = indexById(nodes);

        WorkflowNodeDto entry = byId.get(entryNodeId);
        if (entry == null) {
            throw new IllegalArgumentException("Entry node not found: " + entryNodeId);
        }
        String type = entry.type();
        if (!VALID_ENTRY_TYPES.contains(type)) {
            throw new IllegalArgumentException("Entry node must be one of " + VALID_ENTRY_TYPES + ", got: " + type);
        }
        log.debug("Entry node type={} id={}", type, entryNodeId);

        Map<String, ChatModel> chatModels = new HashMap<>();
        Map<String, Object> runnables = new HashMap<>();

        for (WorkflowNodeDto node : nodes) {
            if ("llm".equals(node.type())) {
                ChatModel model = chatModelFactory.build(node.baseUrl(), node.modelName());
                chatModels.put(node.id(), model);
                log.debug("Built LLM node id={}", node.id());
            }
        }
        for (WorkflowNodeDto node : nodes) {
            if ("agent".equals(node.type())) {
                buildAgent(node, chatModels, runnables);
            }
        }
        for (WorkflowNodeDto node : nodes) {
            if ("sequence".equals(node.type())) {
                buildSequence(node, runnables);
            }
        }
        for (WorkflowNodeDto node : nodes) {
            if ("parallel".equals(node.type())) {
                buildParallel(node, runnables);
            }
        }
        for (WorkflowNodeDto node : nodes) {
            if ("conditional".equals(node.type())) {
                buildConditional(node, byId, runnables);
            }
        }
        for (WorkflowNodeDto node : nodes) {
            if ("supervisor".equals(node.type())) {
                buildSupervisor(node, chatModels, runnables);
            }
        }

        Object entryRunnable = runnables.get(entryNodeId);
        if (entryRunnable == null) {
            throw new IllegalArgumentException("Could not build runnable for entry node: " + entryNodeId);
        }
        log.info("Runnable built successfully for entryNodeId={}", entryNodeId);
        return toWorkflowRunnable(entryRunnable);
    }

    private WorkflowRunnable toWorkflowRunnable(Object runnable) {
        if (runnable instanceof UntypedAgent agent) {
            return agent::invoke;
        }
        if (runnable instanceof dev.langchain4j.agentic.supervisor.SupervisorAgent supervisor) {
            return input -> supervisor.invoke(userMessageFromScope(input));
        }
        return input -> {
            try {
                return runnable.getClass().getMethod("invoke", Map.class).invoke(runnable, input);
            } catch (Exception e) {
                throw new IllegalStateException("Cannot invoke entry runnable", e);
            }
        };
    }

    private static Map<String, WorkflowNodeDto> indexById(List<WorkflowNodeDto> nodes) {
        Map<String, WorkflowNodeDto> map = new HashMap<>();
        for (WorkflowNodeDto n : nodes) {
            map.put(n.id(), n);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private void buildAgent(WorkflowNodeDto node, Map<String, ChatModel> chatModels, Map<String, Object> runnables) {
        if (runnables.containsKey(node.id())) return;

        String llmId = node.llmId();
        if (llmId == null || llmId.isBlank()) {
            throw new IllegalArgumentException("Agent node " + node.id() + " has no llmId");
        }
        ChatModel chatModel = chatModels.get(llmId);
        if (chatModel == null) {
            throw new IllegalArgumentException("LLM node not found for agent " + node.id() + ": " + llmId);
        }

        Object[] tools = toolRegistry.getTools(node.toolIds() != null ? node.toolIds() : List.of());
        var builder = AgenticServices.agentBuilder()
                .chatModel(chatModel)
                .name(node.name() != null ? node.name() : node.id())
                .userMessageProvider(this::userMessageFromScope);
        if (node.outputKey() != null && !node.outputKey().isBlank()) {
            builder.outputKey(node.outputKey());
        }
        if (tools.length > 0) {
            builder.tools(tools);
        }
        runnables.put(node.id(), builder.build());
    }

    private void buildSequence(WorkflowNodeDto node, Map<String, Object> runnables) {
        if (runnables.containsKey(node.id())) return;

        List<String> subIds = node.subAgentIds();
        if (subIds == null || subIds.isEmpty()) {
            throw new IllegalArgumentException("Sequence node " + node.id() + " has no subAgentIds");
        }
        for (String subId : subIds) {
            if (!runnables.containsKey(subId)) {
                throw new IllegalArgumentException("Sub-agent not built for sequence " + node.id() + ": " + subId);
            }
        }

        UntypedAgent[] subAgents = subIds.stream().map(runnables::get).map(UntypedAgent.class::cast).toArray(UntypedAgent[]::new);
        var builder = AgenticServices.sequenceBuilder()
                .subAgents(subAgents)
                .outputKey(node.outputKey() != null && !node.outputKey().isBlank() ? node.outputKey() : "result");
        runnables.put(node.id(), builder.build());
    }

    private void buildParallel(WorkflowNodeDto node, Map<String, Object> runnables) {
        if (runnables.containsKey(node.id())) return;

        List<String> subIds = node.subAgentIds();
        if (subIds == null || subIds.isEmpty()) {
            throw new IllegalArgumentException("Parallel node " + node.id() + " has no subAgentIds");
        }
        for (String subId : subIds) {
            if (!runnables.containsKey(subId)) {
                throw new IllegalArgumentException("Sub-agent not built for parallel " + node.id() + ": " + subId);
            }
        }

        UntypedAgent[] subAgents = subIds.stream().map(runnables::get).map(UntypedAgent.class::cast).toArray(UntypedAgent[]::new);
        var builder = AgenticServices.parallelBuilder()
                .subAgents(subAgents)
                .outputKey(node.outputKey() != null && !node.outputKey().isBlank() ? node.outputKey() : "result");
        Integer poolSize = node.threadPoolSize();
        if (poolSize != null && poolSize > 0) {
            builder.executor(Executors.newFixedThreadPool(poolSize));
        }
        runnables.put(node.id(), builder.build());
    }

    private void buildConditional(WorkflowNodeDto node, Map<String, WorkflowNodeDto> byId, Map<String, Object> runnables) {
        if (runnables.containsKey(node.id())) return;

        String routerId = node.routerAgentId();
        if (routerId == null || routerId.isBlank()) {
            throw new IllegalArgumentException("Conditional node " + node.id() + " has no routerAgentId");
        }
        List<ConditionalBranchDto> branches = node.branches();
        if (branches == null || branches.isEmpty()) {
            throw new IllegalArgumentException("Conditional node " + node.id() + " has no branches");
        }

        var condBuilder = AgenticServices.conditionalBuilder();
        for (ConditionalBranchDto branch : branches) {
            if (!runnables.containsKey(branch.agentId())) {
                throw new IllegalArgumentException("Branch agent not built for conditional " + node.id() + ": " + branch.agentId());
            }
            String conditionKey = branch.conditionKey();
            String value = branch.value();
            Object agent = runnables.get(branch.agentId());
            condBuilder.subAgents(
                    scope -> Objects.equals(value, scope.readState(conditionKey)),
                    agent
            );
        }
        if (node.outputKey() != null && !node.outputKey().isBlank()) {
            condBuilder.outputKey(node.outputKey());
        }
        runnables.put(node.id(), condBuilder.build());
    }

    private void buildSupervisor(WorkflowNodeDto node, Map<String, ChatModel> chatModels, Map<String, Object> runnables) {
        if (runnables.containsKey(node.id())) return;

        String llmId = node.llmId();
        if (llmId == null || llmId.isBlank()) {
            throw new IllegalArgumentException("Supervisor node " + node.id() + " has no llmId");
        }
        ChatModel chatModel = chatModels.get(llmId);
        if (chatModel == null) {
            throw new IllegalArgumentException("LLM node not found for supervisor " + node.id() + ": " + llmId);
        }
        List<String> subIds = node.subAgentIds();
        if (subIds == null || subIds.isEmpty()) {
            throw new IllegalArgumentException("Supervisor node " + node.id() + " has no subAgentIds");
        }
        for (String subId : subIds) {
            if (!runnables.containsKey(subId)) {
                throw new IllegalArgumentException("Sub-agent not built for supervisor " + node.id() + ": " + subId);
            }
        }

        Object[] subAgents = subIds.stream().map(runnables::get).toArray();
        var builder = AgenticServices.supervisorBuilder()
                .chatModel(chatModel)
                .name(node.name() != null ? node.name() : node.id())
                .subAgents(subAgents);
        String strategy = node.responseStrategy();
        if (strategy != null && !strategy.isBlank()) {
            try {
                builder.responseStrategy(SupervisorResponseStrategy.valueOf(strategy.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                builder.responseStrategy(SupervisorResponseStrategy.LAST);
            }
        }
        runnables.put(node.id(), builder.build());
    }

    /**
     * Builds a user prompt from the agentic scope per framework contract.
     * UntypedAgent.invoke(@V("input") Map input) binds the run input to scope state under "input".
     * We read that map (or the scope state directly) and extract a prompt from "metadata"
     * (preferred), then legacy "message", then fallback to formatted key-value pairs.
     */
    private String userMessageFromScope(Object scope) {
        if (scope == null) {
            log.debug("userMessageFromScope: scope=null");
            return "Please respond.";
        }
        Map<?, ?> map = mapFromScope(scope);
        if (map == null || map.isEmpty()) {
            Map<?, ?> managedScopeMap = mapFromCurrentManagedScope();
            if (managedScopeMap != null && !managedScopeMap.isEmpty()) {
                map = managedScopeMap;
                log.info("userMessageFromScope: using current managed scope keys={}", map.keySet());
            }
        }
        if (map != null && !map.isEmpty()) {
            String msg = messageFromMap(map);
            if (msg != null) {
                log.info("userMessageFromScope: resolved userMessage length={}", msg.length());
                return msg;
            }
        }
        String s = scope.toString();
        String fallback = "default".equalsIgnoreCase(s != null ? s.trim() : "") ? "Please help me with a short task." : (s != null ? s : "Please respond.");
        log.warn("userMessageFromScope: no prompt in scope, using fallback length={}", fallback.length());
        return fallback;
    }

    private Map<?, ?> mapFromScope(Object scope) {
        if (scope instanceof AgenticScope agenticScope) {
            return mapFromAgenticScope(agenticScope);
        }
        if (scope instanceof Map<?, ?> m) {
            return m;
        }
        return null;
    }

    private Map<?, ?> mapFromCurrentManagedScope() {
        Map<Class<? extends LangChain4jManaged>, LangChain4jManaged> managed = LangChain4jManaged.current();
        if (managed == null || managed.isEmpty()) {
            return null;
        }
        LangChain4jManaged currentScope = managed.get(AgenticScope.class);
        if (currentScope instanceof AgenticScope agenticScope) {
            return mapFromAgenticScope(agenticScope);
        }
        return null;
    }

    private Map<?, ?> mapFromAgenticScope(AgenticScope agenticScope) {
        if (agenticScope == null) {
            return null;
        }
        Map<?, ?> state = agenticScope.state();
        if (state == null || state.isEmpty()) {
            log.debug("userMessageFromScope: AgenticScope state empty or null");
            return null;
        }
        Object input = state.get("input");
        if (input instanceof Map<?, ?> inputMap && !inputMap.isEmpty()) {
            log.info("userMessageFromScope: using scope state 'input' map keys={}", inputMap.keySet());
            return inputMap;
        }
        return state;
    }

    private String messageFromMap(Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        Object metadata = map.get("metadata");
        String fromMetadata = messageFromMetadata(metadata);
        if (fromMetadata != null) {
            return fromMetadata;
        }
        Object message = map.get("message");
        if (message != null && !message.toString().isBlank()) {
            String msg = message.toString().trim();
            if ("default".equalsIgnoreCase(msg)) {
                return "Please help me with a short task.";
            }
            return msg;
        }
        String formatted = formatMap(map);
        if (formatted != null && !formatted.isBlank() && !"default".equalsIgnoreCase(formatted.trim())) {
            return formatted;
        }
        return null;
    }

    private String messageFromMetadata(Object metadata) {
        if (metadata instanceof Map<?, ?> metadataMap && !metadataMap.isEmpty()) {
            for (String key : List.of("prompt", "query", "task", "instruction", "request", "text", "message")) {
                String text = valueByKeyIgnoreCase(metadataMap, key);
                if (text != null) {
                    if ("default".equalsIgnoreCase(text)) {
                        return "Please help me with a short task.";
                    }
                    return text;
                }
            }
            String formattedMetadata = formatMap(metadataMap);
            if (formattedMetadata != null && !formattedMetadata.isBlank()) {
                return formattedMetadata;
            }
        }
        if (metadata != null) {
            String text = metadata.toString().trim();
            if (!text.isBlank() && !"default".equalsIgnoreCase(text)) {
                return text;
            }
        }
        return null;
    }

    private String valueByKeyIgnoreCase(Map<?, ?> map, String expectedKey) {
        if (map == null || map.isEmpty() || expectedKey == null || expectedKey.isBlank()) {
            return null;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            Object value = entry.getValue();
            if (key == null || value == null) {
                continue;
            }
            if (expectedKey.equalsIgnoreCase(key.toString())) {
                String text = value.toString().trim();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private String formatMap(Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        return map.entrySet().stream()
                .filter(e -> e.getKey() != null && e.getValue() != null)
                .map(e -> e.getKey() + ": " + e.getValue())
                .reduce((a, b) -> a + "\n" + b)
                .orElse(null);
    }
}
