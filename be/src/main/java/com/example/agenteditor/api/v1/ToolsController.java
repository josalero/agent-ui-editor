package com.example.agenteditor.api.v1;

import com.example.agenteditor.api.v1.dto.ToolInfoDto;
import com.example.agenteditor.tools.ToolRegistry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for listing default tools available to agent nodes.
 */
@RestController
@RequestMapping("/api/v1/tools")
@RequiredArgsConstructor
@Slf4j
public class ToolsController {

    private static final Map<String, String> DESCRIPTIONS = Map.of(
            "time", "Current date and time in UTC (ISO-8601)",
            "calculator", "Evaluate arithmetic expressions (e.g. 2 + 3 * 4)"
    );

    private final ToolRegistry toolRegistry;

    @GetMapping
    public List<ToolInfoDto> list() {
        List<String> ids = toolRegistry.getAvailableToolIds();
        log.debug("Listing available tools count={}", ids.size());
        return ids.stream()
                .map(id -> new ToolInfoDto(id, DESCRIPTIONS.getOrDefault(id, "")))
                .collect(Collectors.toList());
    }
}
