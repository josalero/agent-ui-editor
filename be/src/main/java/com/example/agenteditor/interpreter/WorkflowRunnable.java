package com.example.agenteditor.interpreter;

import java.util.Map;

/**
 * Invokable entry point for a workflow. Abstracts over UntypedAgent and supervisor
 * so the run API can invoke with a single call.
 */
@FunctionalInterface
public interface WorkflowRunnable {

    Object run(Map<String, Object> input);
}
