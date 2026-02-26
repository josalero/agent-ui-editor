package com.example.agenteditor.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("OpenRouterChatModelFactory")
class OpenRouterChatModelFactoryTest {

    @Test
    @DisplayName("throws when API key is blank")
    void throwsWhenApiKeyBlank() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new OpenRouterChatModelFactory("", "https://test", "model"));
        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().contains("API key"));
    }

    @Test
    @DisplayName("build returns ChatModel when key is set")
    void buildReturnsChatModel() {
        var factory = new OpenRouterChatModelFactory("test-key", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini");
        assertNotNull(factory.build(null, null));
        assertNotNull(factory.build("https://other", "other-model"));
    }
}
