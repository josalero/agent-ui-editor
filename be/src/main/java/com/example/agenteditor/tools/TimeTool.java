package com.example.agenteditor.tools;

import dev.langchain4j.agent.tool.Tool;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Tool that returns the current time in UTC (ISO-8601).
 */
public class TimeTool {

    @Tool("Get the current date and time in UTC (ISO-8601)")
    public String currentTimeUtc() {
        return Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
