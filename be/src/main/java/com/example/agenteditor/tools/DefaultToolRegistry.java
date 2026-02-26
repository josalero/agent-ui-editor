package com.example.agenteditor.tools;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link ToolRegistry} registering "time" and "calculator" tools.
 */
@Component
public class DefaultToolRegistry implements ToolRegistry {

    private static final Map<String, Object> TOOLS = Map.of(
            "time", new TimeTool(),
            "calculator", new CalculatorTool()
    );

    @Override
    public Object[] getTools(List<String> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return new Object[0];
        }
        List<Object> result = new ArrayList<>();
        for (String id : toolIds) {
            Object tool = TOOLS.get(id);
            if (tool != null) {
                result.add(tool);
            }
        }
        return result.toArray();
    }

    @Override
    public List<String> getAvailableToolIds() {
        return TOOLS.keySet().stream().sorted().collect(Collectors.toList());
    }
}
