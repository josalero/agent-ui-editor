package com.example.agenteditor.llm;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Builds a {@link ChatModel} for OpenRouter (OpenAI-compatible API).
 * API key is read from config/env only; startup fails if key is missing.
 */
@Component
public class OpenRouterChatModelFactory {

    private final String apiKey;
    private final String defaultBaseUrl;
    private final String defaultModel;

    public OpenRouterChatModelFactory(
            @Value("${openrouter.api-key:}") String apiKey,
            @Value("${openrouter.base-url:https://openrouter.ai/api/v1}") String defaultBaseUrl,
            @Value("${openrouter.model:openai/gpt-4o-mini}") String defaultModel) {
        String key = apiKey != null ? apiKey.trim() : "";
        if (key.isEmpty()) {
            throw new IllegalStateException(
                    "OpenRouter API key is required. Set OPENROUTER_API_KEY in the environment or openrouter.api-key in configuration.");
        }
        this.apiKey = key;
        this.defaultBaseUrl = defaultBaseUrl != null && !defaultBaseUrl.isBlank() ? defaultBaseUrl.trim() : "https://openrouter.ai/api/v1";
        this.defaultModel = defaultModel != null && !defaultModel.isBlank() ? defaultModel.trim() : "openai/gpt-4o-mini";
    }

    /**
     * Builds a ChatModel using the given base URL and model name.
     * If either is null or blank, the configured default is used.
     */
    public ChatModel build(String baseUrl, String modelName) {
        String url = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.trim() : defaultBaseUrl;
        String model = (modelName != null && !modelName.isBlank()) ? modelName.trim() : defaultModel;
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(url)
                .modelName(model)
                .timeout(Duration.ofSeconds(120))
                .build();
    }
}
