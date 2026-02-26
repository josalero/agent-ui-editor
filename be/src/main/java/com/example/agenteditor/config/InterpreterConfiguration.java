package com.example.agenteditor.config;

import com.example.agenteditor.interpreter.WorkflowGraphInterpreter;
import com.example.agenteditor.llm.OpenRouterChatModelFactory;
import com.example.agenteditor.tools.ToolRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InterpreterConfiguration {

    @Bean
    public WorkflowGraphInterpreter workflowGraphInterpreter(
            OpenRouterChatModelFactory chatModelFactory,
            ToolRegistry toolRegistry) {
        return new WorkflowGraphInterpreter(chatModelFactory, toolRegistry);
    }
}
