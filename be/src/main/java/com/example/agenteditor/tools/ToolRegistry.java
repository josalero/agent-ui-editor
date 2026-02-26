package com.example.agenteditor.tools;

import java.util.List;

/**
 * Registry of tools by id. Used by the interpreter to resolve toolIds on agent nodes.
 */
public interface ToolRegistry {

    /**
     * Returns tool instances for the given ids, in order. Unknown ids are skipped.
     */
    Object[] getTools(List<String> toolIds);

    /**
     * Returns the list of available tool ids that can be assigned to agent nodes.
     */
    List<String> getAvailableToolIds();
}
