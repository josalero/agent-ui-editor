package com.example.agenteditor.llm;

import dev.langchain4j.model.chat.ChatModel;

/**
 * Test double: always returns the same {@link ChatModel} regardless of baseUrl/modelName.
 */
public class StubOpenRouterChatModelFactory extends OpenRouterChatModelFactory {

    private final ChatModel stub;

    /**
     * Creates a factory that returns the given stub for any build() call.
     * Uses a dummy API key so the parent constructor passes validation.
     */
    public StubOpenRouterChatModelFactory(ChatModel stub) {
        super("test-key", "https://test", "test-model");
        this.stub = stub;
    }

    @Override
    public ChatModel build(String baseUrl, String modelName) {
        return stub;
    }

    @Override
    public ChatModel build(String baseUrl, String modelName, Double temperature, Integer maxTokens) {
        return stub;
    }
}
