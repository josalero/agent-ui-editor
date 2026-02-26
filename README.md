# Agent UI Editor

Visual editor for building and running agent workflows (LLM, Agent, Sequence, Parallel, Conditional, Supervisor) with a Spring Boot backend and React frontend.

## Quick Start

```bash
# Build frontend + backend and run tests
./gradlew build

# Run backend (serves API + embedded frontend)
./gradlew :be:bootRun
```

- UI: http://localhost:8085
- API: http://localhost:8085/api/v1/
- Health: http://localhost:8085/api/v1/health

Default port is `8085` (see `be/src/main/resources/application.yml`).

## Key Features

- n8n-style workflow editor with node palette and graph canvas.
- Auto layout with an orchestrator spine (entry on left, primary flow to the right) and attached dependency rows under each node (for LLM/router links).
- Tool assignments on agents are visualized as tool nodes connected to the owning sub-agent/supervisor.
- Visual connection semantics for orchestration edges (`delegates`, `model`, `router`, `tool`, `branch`).
- Node cards and palette now use dedicated icons/labels per type (`LLM`, `Agent`, `Supervisor`, `Sequence`, `Parallel`, `Conditional`).
- Left panel includes an `Agents` list for quick selection/focus on the canvas.
- UI rule: adding a new `Agent` or `Supervisor` auto-creates a paired dedicated `LLM` node and links it.
- Entry node is restricted to: `sequence`, `parallel`, or `supervisor`.
- LLM node setup supports `baseUrl`, `modelName`, `temperature`, and `maxTokens`.
- Workflow CRUD + run API.
- Example workflows are refreshed at backend startup (same sample names are updated in place).

## Visual Workflow Example

```mermaid
flowchart LR
  seq["SEQUENCE<br/>seq-story<br/><small>story<br/>Entry</small>"]
  writer["AGENT<br/>CreativeWriter<br/><small>Story writer<br/>Executed</small>"]
  editor["AGENT<br/>StyleEditor<br/><small>Story editor<br/>Executed</small>"]
  llmWriter["LLM<br/>llm-writer<br/><small>openai/gpt-4o-mini</small>"]
  llmEditor["LLM<br/>llm-editor<br/><small>openai/gpt-4o-mini</small>"]
  toolCalc["TOOL<br/>Calculator<br/><small>Evaluate arithmetic expressions</small>"]
  toolTime["TOOL<br/>Time<br/><small>Current date/time</small>"]

  seq -. "delegates" .-> writer
  seq -. "delegates" .-> editor

  writer -. "model" .-> llmWriter
  editor -. "model" .-> llmEditor

  writer -. "tool" .-> toolCalc
  editor -. "tool" .-> toolTime

  classDef sequence fill:#dff4ea,stroke:#0f766e,stroke-width:2.2px,color:#14532d;
  classDef agent fill:#f5f3ff,stroke:#6d28d9,stroke-width:2px,color:#312e81;
  classDef llm fill:#eff6ff,stroke:#2563eb,stroke-width:2px,color:#1e3a8a;
  classDef tool fill:#fff7ed,stroke:#c2410c,stroke-width:2px,color:#7c2d12;
  classDef executed stroke:#22c55e,stroke-width:3px;

  class seq sequence;
  class writer,editor agent;
  class llmWriter,llmEditor llm;
  class toolCalc,toolTime tool;
  class writer,editor executed;

  linkStyle 0,1 stroke:#0f766e,stroke-width:2.4px,stroke-dasharray:6 5;
  linkStyle 2,3 stroke:#2563eb,stroke-width:2.2px,stroke-dasharray:6 4;
  linkStyle 4,5 stroke:#c2410c,stroke-width:2.2px,stroke-dasharray:2 5;
```

## Run Payload Format

Use `metadata` for run input. Example:

```json
{
  "metadata": {
    "prompt": "Write a short noir story about a robot in Paris.",
    "topic": "a robot in Paris",
    "style": "noir"
  }
}
```

The backend uses `metadata.prompt` as the primary prompt and treats other `metadata.*` keys as context.

`parallel` can be used as an entry node, but for clearer final responses prefer `sequence`/`supervisor` as entry and place `parallel` inside it, followed by a composing agent.

Run responses also include execution trace fields:

- `executedNodeIds`: node IDs that were executed (ordered, deduplicated).
- `executedNodeNames`: human-readable names for those executed nodes.

The editor uses this to highlight executed nodes after each run.

## Sub-Agent Prompt Configuration

For `agent` nodes (including sub-agents inside sequence/parallel/supervisor), you can now set:

- `role`: short role label for the agent.
- `systemMessage`: system instruction for that specific agent.
- `promptTemplate`: per-agent prompt template with placeholders (for example `{{metadata.prompt}}`, `{{metadata.topic}}`, `{{metadata.style}}`).

Template variables are resolved from the run input map. Recommended run input pattern:

```json
{
  "metadata": {
    "prompt": "Write a short noir story about a robot in Paris.",
    "topic": "a robot in Paris",
    "style": "noir"
  }
}
```

Example agent node:

```json
{
  "id": "writer",
  "type": "agent",
  "llmId": "llm-1",
  "name": "CreativeWriter",
  "role": "Story writer",
  "systemMessage": "You write concise, vivid first drafts.",
  "promptTemplate": "Write a short story about {{metadata.topic}} in {{metadata.style}} style.",
  "outputKey": "story"
}
```

## Project Docs

- [docs/agent-editor.md](docs/agent-editor.md): main user/developer guide.
- [docs/agent-editor-ui-guide.md](docs/agent-editor-ui-guide.md): detailed UI usage.

## Testing

```bash
# Backend tests (includes sample workflow integration coverage)
./gradlew :be:test

# Run only sample workflow integration test
./gradlew :be:test --tests '*SampleWorkflowsIntegrationTest'
```

## Security Notes

Do not commit passwords or API keys. Set secrets with environment variables (for example `OPENROUTER_API_KEY`).
