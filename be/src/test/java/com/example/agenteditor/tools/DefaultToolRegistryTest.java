package com.example.agenteditor.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DefaultToolRegistry")
class DefaultToolRegistryTest {

    private final DefaultToolRegistry registry = new DefaultToolRegistry();

    @Test
    @DisplayName("returns time and calculator tools for known ids")
    void returnsToolsForKnownIds() {
        Object[] tools = registry.getTools(List.of("time", "calculator"));
        assertNotNull(tools);
        assertEquals(2, tools.length);
        assertNotNull(tools[0]);
        assertNotNull(tools[1]);
    }

    @Test
    @DisplayName("returns empty array for empty list")
    void returnsEmptyForEmptyList() {
        Object[] tools = registry.getTools(List.of());
        assertNotNull(tools);
        assertEquals(0, tools.length);
    }

    @Test
    @DisplayName("skips unknown ids")
    void skipsUnknownIds() {
        Object[] tools = registry.getTools(List.of("unknown", "time"));
        assertNotNull(tools);
        assertEquals(1, tools.length);
    }

    @Test
    @DisplayName("returns available tool ids sorted")
    void returnsAvailableToolIds() {
        List<String> ids = registry.getAvailableToolIds();
        assertNotNull(ids);
        assertEquals(2, ids.size());
        assertTrue(ids.contains("time"));
        assertTrue(ids.contains("calculator"));
        assertEquals(List.of("calculator", "time"), ids);
    }
}
