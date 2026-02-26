package com.example.agenteditor.llm;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;

import java.util.List;

/**
 * Stub {@link ChatModel} for tests. Returns a fixed text for any request.
 */
public class StubChatModel implements ChatModel {

    private final String fixedReply;

    public StubChatModel(String fixedReply) {
        this.fixedReply = fixedReply != null ? fixedReply : "ok";
    }

    public StubChatModel() {
        this("ok");
    }

    @Override
    public ChatResponse chat(List<dev.langchain4j.data.message.ChatMessage> messages) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(fixedReply))
                .finishReason(FinishReason.STOP)
                .build();
    }
}
