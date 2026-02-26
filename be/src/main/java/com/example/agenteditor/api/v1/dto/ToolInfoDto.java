package com.example.agenteditor.api.v1.dto;

/**
 * API response for one available tool (id and optional description).
 */
public record ToolInfoDto(String id, String description) {}
