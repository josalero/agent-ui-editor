# Agent UI Editor — User guide

Visual editor for composing agent workflows (LLM, agents, sequences, parallel, conditional, supervisor). Build once, run the backend; the UI and API are served from a single port.

## Build and run

**Prerequisites:** Java 25, Node.js (for frontend build), Gradle.

**Secrets:** Do not put passwords or API keys in `application.yml` or in any committed file. The OpenRouter API key must be set via the **environment variable** `OPENROUTER_API_KEY` (or `openrouter.api-key` in an external config). Run workflows that call an LLM only when this variable is set to a valid key in your environment.

1. **Full build** (frontend prod bundle + backend):

   ```bash
   ./gradlew build
   ```

   This builds the React app (`:fe:build`), copies `fe/dist/*` into the backend static resources, and runs backend tests.

2. **Run the application:**

   ```bash
   ./gradlew :be:bootRun
   ```

   Default port: **8085** (configurable via `server.port` in `application.yml`).

3. **Open in browser:**

   - UI: **http://localhost:8085**
   - API: **http://localhost:8085/api/v1/**

## Frontend development

To run the frontend dev server with hot reload (Vite on port 5173), proxy to the backend:

```bash
cd fe && npm run dev
```

Then open **http://localhost:5173**. API calls are proxied to the backend (configure `vite.config.ts` proxy to `http://localhost:8085` or your `server.port`). CORS is enabled for `http://localhost:5173` so the dev server can call the API.

## Workflow API

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/workflows` | Create workflow (body: `name`, `entryNodeId`, `nodes`). Returns `{ "id": "uuid" }`. |
| `GET` | `/api/v1/workflows` | List workflows. Returns `{ "workflows": [ { "id", "name", "updatedAt" } ] }`. |
| `GET` | `/api/v1/workflows/samples` | List seeded sample workflows only. |
| `GET` | `/api/v1/workflows/{id}` | Get full workflow (nodes, entryNodeId, etc.). |
| `PUT` | `/api/v1/workflows/{id}` | Update workflow (same body as create). |
| `DELETE` | `/api/v1/workflows/{id}` | Delete workflow. Returns 204. |
| `POST` | `/api/v1/workflows/{id}/run` | Run workflow. Body: JSON object (e.g. `{ "metadata": { "prompt": "Hello", "topic": "test" } }`). Returns `{ "result": "...", "executedNodeIds": ["..."], "executedNodeNames": ["..."] }`. |

## Using the UI

- **Workflows list:** Open `/` to see all workflows. Use “Create workflow” or “Run” per row.
- **Editor:** Open a workflow or “Create workflow” to use the canvas. Add nodes from the palette (LLM, Agent, Supervisor, Sequence, Parallel, Conditional), use the left `Agents` list to focus existing agents quickly, connect nodes with labeled orchestration edges (`uses LLM`, `sub-agent`, `router`, `branch`), select a node to edit fields in the side panel, and use “Set as entry” to mark the entry node. Save to create or update; Run (when the workflow is saved) opens a dialog to send input JSON and view the result.
- **Execution trace view:** After a run, the dialog shows executed nodes and the editor highlights executed nodes on the canvas.
- **Per-subagent prompts:** For each `agent` node, you can set `role`, `systemMessage`, and `promptTemplate` in the node panel. `promptTemplate` supports placeholders from run input (for example `{{metadata.prompt}}`, `{{metadata.topic}}`, `{{metadata.style}}`).
- **Entry recommendation:** `parallel` can be an entry node, but usually clearer outputs come from `sequence` or `supervisor` as entry, with `parallel` used as an inner step plus a final composing agent.

Errors from the API (e.g. validation) are shown in the UI; stack traces and PII are never exposed.

**Configuration and secrets:** Application config (`application.yml`) uses environment variables for any secret (e.g. `OPENROUTER_API_KEY`, `SPRING_DATASOURCE_PASSWORD`). Never commit real passwords or API keys in YAML, documentation, or code.

For detailed **user guidance on the UI** (list, editor, add/connect/delete nodes, set entry, save, run), see **[Agent Editor UI Guide](agent-editor-ui-guide.md)**.
