# Agent UI Editor — Implementation Plan

This document is a step-by-step plan to build a basic n8n-like visual editor for agent configurations. The editor will expose all current agent patterns (single agent, supervisor, sequential, parallel, conditional) as nodes on a canvas and allow designing, saving, and running workflows without code changes.

**All implementation work is done in this repository:** [`agent-ui-editor`](.) — a Gradle multi-module project with a frontend module (`fe`) and a backend module (`be`). The backend can be run against the test project’s agent patterns or standalone. **LLM:** All agents use **OpenRouter** for LLM support (OpenAI-compatible API). The backend reads the OpenRouter API key from the environment; base URL and model can come from the workflow graph or from application config. See §2.4 and the code details in each phase.

---

## 1. Goal and scope

| Goal | Deliver a visual flow editor where users can compose agents and workflows, persist definitions, and execute them via API. |
| Scope | Covers: LLM config, single agents (with tools), supervisor, sequential workflow, parallel workflow, conditional workflow. Backend and frontend live in `agent-ui-editor` (Gradle modules `be` and `fe`). |
| Out of scope (v1) | Custom code nodes, versioning of workflows, multi-tenant isolation, real-time collaboration. |

---

## 2. Project structure and tech stack

The **agent-ui-editor** project is a **Gradle multi-module** build with two subprojects:

```
agent-ui-editor/
├── settings.gradle          # root: include("fe", "be")
├── build.gradle             # root: shared config, no application code
├── fe/                          # frontend module
│   ├── build.gradle         # Gradle node/vite plugin or npm tasks
│   ├── package.json
│   ├── vite.config.ts
│   └── src/...
└── be/                          # backend module
    ├── build.gradle         # Java 25, Spring Boot 4.0.3
    └── src/
        ├── main/java/...
        └── test/java/...
```

### 2.1 Frontend module (`fe`)

| Choice | Requirement |
|--------|-------------|
| **Build / dev server** | [Vite](https://vite.dev/) — use the latest stable version. |
| **Framework** | [React](https://react.dev/) — use the latest stable version (React 19.x when available). |
| **Package manager** | npm or pnpm (as preferred). |

- The `fe` module is a standard Vite + React app (e.g. created with `npm create vite@latest fe -- --template react` or equivalent), placed under the `agent-ui-editor` repo and wired into the root Gradle build so that `./gradlew :fe:build` (or `:fe:run`) runs frontend build/dev tasks.
- Use the **latest stable** React and Vite versions at the time of implementation; keep dependencies up to date in `fe/package.json`.

### 2.2 Backend module (`be`)

| Choice | Requirement |
|--------|-------------|
| **Language** | Java **25**. |
| **Framework** | Spring Boot **4.0.3**. |

- The `be` module is a Spring Boot application: REST API, workflow CRUD, graph interpreter, and run API. It uses Java 25 and Spring Boot 4.0.3 as defined in `be/build.gradle`.
- All Phase 1–3 work (graph model, persistence, interpreter, run API) is implemented in `be/`.

### 2.3 Root Gradle setup

- **Root `settings.gradle`:** `rootProject.name = "agent-ui-editor"` and `include("fe", "be")`.
- **Root `build.gradle`:** Optional shared conventions; no application code. Subprojects are configured in their own `fe/build.gradle` and `be/build.gradle`.
- **`fe` build:** Use the [Gradle Node plugin](https://github.com/node-gradle/gradle-node-plugin) or an `Exec` task to run `npm run build` / `npm run dev` so the root build can drive the frontend (e.g. `./gradlew :fe:build`, `./gradlew :fe:run` for dev).
- **`be` build:** Standard `org.springframework.boot` and `java` plugins; `java { toolchain { languageVersion.set(JavaLanguageVersion.of(25)) } }`; Spring Boot version **4.0.3**.

**Unified build and single server (server.port)**

The Java build must produce the React production bundle and serve it from the same server so that one process and one port expose both the API and the UI.

- **Build cycle:** When building **be** (or the root project), (1) run the **fe** production build (e.g. `npm run build` in `fe/` → output in `fe/dist/`), (2) copy `fe/dist/*` into the backend’s static resources (e.g. `be/src/main/resources/static/` or into `be/build/resources/main/static/` via a Gradle task). **be**’s `processResources` (or equivalent) should depend on this so that the built JAR contains the React prod assets. Thus `./gradlew :be:build` or `./gradlew build` compiles Java, builds **fe** prod, copies static files, and produces one runnable JAR.
- **Runtime:** The server runs on `server.port` (e.g. 8080). API: `http://localhost:8080/api/v1/*`. Frontend: same origin — `GET /` serves `index.html`; static assets (JS, CSS) from `/assets/` or `/` as emitted by Vite. For client-side routes (e.g. `/workflows/:id/edit`), the server must serve `index.html` for non-API, non-file requests (SPA fallback) so the React router works. Configure Spring Boot (e.g. `WebMvcConfigurer` or a controller) to return `index.html` for `GET /*` when the path does not start with `/api` and no static resource exists.
- **fe API base in prod:** The React app uses a relative API base (e.g. `''` or `'/api'`) so all requests go to the same origin and port. No CORS needed when serving from the same server.
- **Result:** User opens `http://localhost:8080` and gets the editor; API is at `http://localhost:8080/api/v1/...`. One deployment, one port.

### 2.4 LLM: OpenRouter

All agents in the graph interpreter use **OpenRouter** as the LLM provider:

- **API:** OpenAI-compatible; use `dev.langchain4j.model.openai.OpenAiChatModel` with OpenRouter's base URL.
- **Base URL:** Default `https://openrouter.ai/api/v1` (overridable per `llm` node or via `openrouter.base-url`).
- **API key:** Read from environment (`OPENROUTER_API_KEY`) or config (`openrouter.api-key`). Never store in the graph or logs.
- **Model:** From the workflow graph `llm` node's `modelName` (e.g. `openai/gpt-4o-mini`), or default from `openrouter.model`. Use any [OpenRouter model ID](https://openrouter.ai/docs/models).

The backend must provide a way to build a `ChatModel` per `llm` node (or a shared bean) using the above; the interpreter uses that when building agents.

### 2.5 Get started

This section defines the **API contracts** (request/response shapes) and **example flows** that cover every node type (LLM, agent, sequence, parallel, conditional, supervisor). Use these as the canonical samples to implement and test against.

**Prerequisites:** Java 25, Node.js (for `fe`), Gradle. Set `OPENROUTER_API_KEY` in the environment (get a key at [OpenRouter Keys](https://openrouter.ai/keys)).

---

#### Sample contracts (API request/response)

**Create workflow — `POST /api/v1/workflows`**

| Request (body) | Response |
|----------------|----------|
| `{ "name": string, "entryNodeId": string, "nodes": WorkflowNode[] }` | `201` + `{ "id": string (UUID) }` |

**List workflows — `GET /api/v1/workflows`**

| Response |
|----------|
| `200` + `{ "workflows": [ { "id": string, "name": string, "updatedAt": string (ISO-8601) } ] }` |

**Get workflow — `GET /api/v1/workflows/{id}`**

| Response |
|----------|
| `200` + `{ "id": string, "name": string, "entryNodeId": string, "nodes": WorkflowNode[], "createdAt": string, "updatedAt": string }` |

**Update workflow — `PUT /api/v1/workflows/{id}`**

| Request (body) | Response |
|----------------|----------|
| Same as create (name, entryNodeId, nodes) | `200` + full workflow object (same shape as Get) |

**Delete workflow — `DELETE /api/v1/workflows/{id}`**

| Response |
|----------|
| `204 No Content` |

**Run workflow — `POST /api/v1/workflows/{id}/run`**

| Request (body) | Response |
|----------------|----------|
| JSON object with input parameters (e.g. `metadata.prompt`, `metadata.topic`, `metadata.style`, `metadata.mood`) | `200` + `{ "result": string }` |

**Error response (4xx/5xx)**

| Response |
|----------|
| `{ "message": string, "errors": [ { "field": string, "message": string } ]? }` — no stack traces or PII. |

**WorkflowNode (per node type):**

- **llm:** `{ "id": string, "type": "llm", "baseUrl": string, "modelName": string }`
- **agent:** `{ "id": string, "type": "agent", "llmId": string, "name": string, "outputKey"?: string, "toolIds"?: string[] }`
- **supervisor:** `{ "id": string, "type": "supervisor", "llmId": string, "subAgentIds": string[], "responseStrategy"?: string }`
- **sequence:** `{ "id": string, "type": "sequence", "subAgentIds": string[], "outputKey": string }`
- **parallel:** `{ "id": string, "type": "parallel", "subAgentIds": string[], "outputKey": string, "threadPoolSize"?: number }`
- **conditional:** `{ "id": string, "type": "conditional", "routerAgentId": string, "branches": [ { "conditionKey": string, "value": string, "agentId": string } ] }`

---

#### Example flows (cover all node types)

Use these flows to build and test the interpreter and run API. Each example is a valid workflow graph you can POST to create, then run with the given input.

**Example 1 — Sequential (Story):** LLM + 2 agents + sequence. Run with `metadata` (e.g. `prompt`, optionally `topic`, `style`).

```json
{
  "name": "Story workflow",
  "entryNodeId": "seq-story",
  "nodes": [
    { "id": "llm-1", "type": "llm", "baseUrl": "https://openrouter.ai/api/v1", "modelName": "openai/gpt-4o-mini" },
    { "id": "writer", "type": "agent", "llmId": "llm-1", "name": "CreativeWriter", "outputKey": "story" },
    { "id": "editor", "type": "agent", "llmId": "llm-1", "name": "StyleEditor", "outputKey": "story" },
    { "id": "seq-story", "type": "sequence", "subAgentIds": ["writer", "editor"], "outputKey": "story" }
  ]
}
```

Run request: `{ "metadata": { "prompt": "Write a short noir story about a robot in Paris.", "topic": "a robot in Paris", "style": "noir" } }` → response `{ "result": "..." }`.

---

**Example 2 — Parallel (Evening plan):** LLM + 2 agents + parallel. Run with `metadata` (e.g. `prompt`, optionally `mood`).

```json
{
  "name": "Evening plan workflow",
  "entryNodeId": "parallel-plan",
  "nodes": [
    { "id": "llm-1", "type": "llm", "baseUrl": "https://openrouter.ai/api/v1", "modelName": "openai/gpt-4o-mini" },
    { "id": "movies", "type": "agent", "llmId": "llm-1", "name": "MovieExpert", "outputKey": "movies" },
    { "id": "meals", "type": "agent", "llmId": "llm-1", "name": "MealExpert", "outputKey": "meals" },
    { "id": "parallel-plan", "type": "parallel", "subAgentIds": ["movies", "meals"], "outputKey": "plan", "threadPoolSize": 2 }
  ]
}
```

Run request: `{ "metadata": { "prompt": "Suggest a cozy evening plan with one movie and one dinner idea.", "mood": "cozy" } }` → response `{ "result": "Movies: ... Meals: ..." }` (combiner merges `movies` and `meals`).

---

**Example 3 — Conditional (Expert router):** LLM + router agent + 3 experts + conditional. Run with `metadata.prompt`.

```json
{
  "name": "Expert router workflow",
  "entryNodeId": "seq-route",
  "nodes": [
    { "id": "llm-1", "type": "llm", "baseUrl": "https://openrouter.ai/api/v1", "modelName": "openai/gpt-4o-mini" },
    { "id": "router", "type": "agent", "llmId": "llm-1", "name": "CategoryRouter", "outputKey": "category" },
    { "id": "general", "type": "agent", "llmId": "llm-1", "name": "GeneralExpert", "outputKey": "response", "toolIds": ["time", "calculator"] },
    { "id": "creative", "type": "agent", "llmId": "llm-1", "name": "CreativeExpert", "outputKey": "response" },
    { "id": "planning", "type": "agent", "llmId": "llm-1", "name": "PlanningExpert", "outputKey": "response" },
    { "id": "cond", "type": "conditional", "routerAgentId": "router", "branches": [
      { "conditionKey": "category", "value": "GENERAL", "agentId": "general" },
      { "conditionKey": "category", "value": "CREATIVE", "agentId": "creative" },
      { "conditionKey": "category", "value": "PLANNING", "agentId": "planning" }
    ]},
    { "id": "seq-route", "type": "sequence", "subAgentIds": ["router", "cond"], "outputKey": "response" }
  ]
}
```

Run request: `{ "metadata": { "prompt": "What time is it?" } }` → router sets category; conditional invokes one expert; response `{ "result": "..." }`.

---

**Example 4 — Supervisor:** LLM + supervisor + 3 sub-agents (assistant, story sequence, evening parallel). Run with `metadata.prompt`; supervisor picks one sub-agent.

```json
{
  "name": "Supervisor workflow",
  "entryNodeId": "sup",
  "nodes": [
    { "id": "llm-1", "type": "llm", "baseUrl": "https://openrouter.ai/api/v1", "modelName": "openai/gpt-4o-mini" },
    { "id": "assistant", "type": "agent", "llmId": "llm-1", "name": "Assistant", "outputKey": "response", "toolIds": ["time", "calculator"] },
    { "id": "writer", "type": "agent", "llmId": "llm-1", "name": "Writer", "outputKey": "story" },
    { "id": "editor", "type": "agent", "llmId": "llm-1", "name": "Editor", "outputKey": "story" },
    { "id": "seq-story", "type": "sequence", "subAgentIds": ["writer", "editor"], "outputKey": "story" },
    { "id": "movies", "type": "agent", "llmId": "llm-1", "name": "MovieExpert", "outputKey": "movies" },
    { "id": "meals", "type": "agent", "llmId": "llm-1", "name": "MealExpert", "outputKey": "meals" },
    { "id": "parallel-plan", "type": "parallel", "subAgentIds": ["movies", "meals"], "outputKey": "plan" },
    { "id": "sup", "type": "supervisor", "llmId": "llm-1", "subAgentIds": ["assistant", "seq-story", "parallel-plan"], "responseStrategy": "LAST" }
  ]
}
```

Run request: `{ "metadata": { "prompt": "Suggest a cozy movie and dinner" } }` → supervisor delegates to `parallel-plan`; response `{ "result": "..." }`.

---

#### Build, run, and try

**1. Clone and build (full cycle: fe prod + be)**

The Java build generates the React production bundle and copies it into the backend so one JAR serves both. From the repo root:

```bash
cd agent-ui-editor
./gradlew build
```

This runs the **fe** prod build, copies `fe/dist/*` into **be** static resources, and builds the **be** JAR. The resulting app serves the API and the UI from the same server.

**2. Start the app (single server.port)**

```bash
export OPENROUTER_API_KEY=your_key_here
./gradlew :be:bootRun
```

Open **http://localhost:8080** for the editor UI and **http://localhost:8080/api/v1/workflows** for the API. Everything is on `server.port` (default 8080).

**3. Create and run Example 1 (sequence)**

The repo can include an `examples/` directory with the four workflow JSONs above (`story-workflow.json`, `evening-plan-workflow.json`, `expert-router-workflow.json`, `supervisor-workflow.json`) for curl and tests. To create and run the story flow:

```bash
# Create
RESP=$(curl -s -X POST http://localhost:8080/api/v1/workflows \
  -H "Content-Type: application/json" \
  -d @examples/story-workflow.json)
WORKFLOW_ID=$(echo "$RESP" | jq -r '.id')

# Run
curl -s -X POST "http://localhost:8080/api/v1/workflows/${WORKFLOW_ID}/run" \
  -H "Content-Type: application/json" \
  -d '{ "metadata": { "prompt": "Write a short noir story about a robot in Paris.", "topic": "a robot in Paris", "style": "noir" } }'
```

**4. Frontend (after Phase 4)**

- **Production / single port:** Use the unified build and run **be** only (§2.3). Open **http://localhost:8080** — the server serves the React app and the API from the same port.
- **Dev (optional):** For a faster UI feedback loop, run **fe** dev server with `./gradlew :fe:run` or `cd fe && npm run dev`, and point the app at the backend (e.g. `VITE_API_BASE=http://localhost:8080`). Configure CORS in **be** for the **fe** dev origin (e.g. `http://localhost:5173`).

---

## 3. Phases overview

| Phase | Name | Outcome |
|-------|------|--------|
| **1** | Graph model & API | JSON schema for workflows; CRUD API to save/load workflow definitions. |
| **2** | Graph interpreter | Backend builds LangChain4j agents from a graph (no UI yet). |
| **3** | Run API | Execute a stored workflow by ID with input payload; return result. |
| **4** | Frontend editor | Canvas (React Flow or similar) with node types and forms; call backend to save/load/run. |
| **5** | Polish & docs | Validation, error handling, tests, and user/developer documentation. |

**Quality gates** apply at the end of each phase; the phase is not considered done until all gates pass. See the "Quality gates" subsection in each phase below.

### 3.1 Best practices and rules

Follow these practices throughout implementation so the project stays maintainable, secure, and consistent.

**Testing**

- New functionality must include unit tests; bug fixes must include a regression test.
- Tests must have meaningful assertions (not only "no exception thrown") and must not depend on external services, network, or execution order.
- Test names must describe the scenario and expected outcome.

**Error handling**

- Catch specific exceptions, not generic `Exception` or `Throwable`; never swallow exceptions silently.
- Error messages must be meaningful and actionable (what failed, what to check).
- Never expose stack traces or internal details in user-facing or API error responses. Log errors at the appropriate level (ERROR for failures, WARN for recoverable issues).

**Security**

- **Secrets:** Never hardcode API keys, tokens, or connection strings. Use environment variables or a secret manager; read OpenRouter API key from env only. Never commit `.env` or key files.
- **PII:** Do not log, expose, or store PII in error messages, responses, or the graph. If in doubt, treat data as PII.
- **Injection:** Use parameterized queries for SQL; sanitize user input before rendering or passing to the LLM/agents. Do not execute user-supplied code.
- **Auth:** In production, protect workflow and run APIs with authentication and authorization; validate on the server side.

**Code style**

- Use self-documenting names (variables, methods, classes). Keep methods focused (single responsibility); prefer composition over inheritance.
- Remove dead code; comments should explain *why*, not restate the code.
- **Backend:** Follow project Java/Spring conventions; use DTOs at API boundaries; constructor injection.
- **Frontend:** Keep components small and composable; centralize API calls and error handling.

**Git and API**

- Branch naming: `{ticket}-{description}` or `feat/agent-editor-phase-1`. Commit messages: conventional commits (`feat:`, `fix:`, `chore:`, `docs:`, `test:`).
- REST: versioned base path (`/api/v1/`), nouns for resources, correct HTTP methods and status codes. Return consistent error response format with a correlation ID when applicable.

---

## Implementation plan with iterations

Execute **one iteration at a time**: complete the tasks, run the quality checks, then **review** the deliverable before starting the next iteration. Use the checklist under **Review** to sign off.

| Iteration | Name | Deliverable | Maps to phase |
|-----------|------|-------------|---------------|
| **1** | Project scaffold | Gradle multi-module, **be** (Java 25, Spring Boot 4.0.3), **fe** placeholder | Phase 1 |
| **2** | Graph model and persistence | DTOs, entity, Flyway, repository, validator | Phase 1 |
| **3** | Workflow CRUD API | Controller, service, five endpoints, unit + integration tests | Phase 1 |
| **4** | OpenRouter, tools, and interpreter (agent + sequence) | ChatModel factory, tool registry, interpreter for llm/agent/sequence | Phase 2 |
| **5** | Interpreter — parallel, conditional, supervisor | Full interpreter for all node types | Phase 2 |
| **6** | Run API | Run endpoint, service, error handling, integration test (story workflow) | Phase 3 |
| **7** | Example workflows and E2E | `examples/` with four JSONs; curl create + run for each | Get started |
| **8** | Frontend scaffold and API client | **fe** Vite+React, Gradle wire, API client module | Phase 4 |
| **9** | Editor canvas and node types | React Flow, palette, custom nodes, add/connect | Phase 4 |
| **10** | Save/load and run from UI | List, editor save/load, run dialog, call **be** | Phase 4 |
| **11** | Polish and documentation | Validation errors, CORS, docs, full quality gates | Phase 5 |

---

### Iteration 1 — Project scaffold

**Scope:** Root Gradle build, **be** Spring Boot app (empty), **fe** placeholder or minimal app. No workflow logic yet.

**Tasks**

- [ ] Create root `settings.gradle`: `rootProject.name = "agent-ui-editor"`, `include("fe", "be")`.
- [ ] Create root `build.gradle`: optional shared config; no application code.
- [ ] Create **be** module: `be/build.gradle` with Java 25 toolchain, Spring Boot 4.0.3, dependencies (spring-boot-starter-web, data-jpa, validation).
- [ ] Create **be** main class: `com.example.agenteditor.AgentEditorApplication` with `@SpringBootApplication`.
- [ ] Create **be** `application.yml`: server port 8080, basic JPA/datasource (e.g. H2 for dev).
- [ ] Create **fe** module: either placeholder `fe/build.gradle` (Exec task for `npm run build`) or scaffold with `npm create vite@latest fe -- --template react` and wire in root (e.g. Node plugin).
- [ ] Plan for unified build (§2.3): **be** build will eventually depend on **fe** build + copy of `fe/dist/*` into **be** static resources; can be added in Iteration 8 or 11 when **fe** exists. For Iteration 1, **be** may have an empty `static/` or no dependency on **fe** yet.
- [ ] Verify: `./gradlew :be:build` passes; `./gradlew :be:bootRun` starts the app.

**Review**

- [ ] Root and **be**/ **fe** structure match §2. No code in root. **be** runs and responds (e.g. empty or health).

---

### Iteration 2 — Graph model and persistence

**Scope:** DTOs for workflow graph, JPA entity, Flyway migration, repository, graph validator. No REST yet.

**Tasks**

- [ ] Add DTOs in **be** (§4.5): `WorkflowCreateRequest`, `WorkflowUpdateRequest`, `WorkflowResponse`, `WorkflowListItem`; node DTOs or records per type (llm, agent, supervisor, sequence, parallel, conditional).
- [ ] Add Flyway migration `V1__create_workflow_definition.sql`: table `workflow_definition` (id UUID, name, entry_node_id, graph_json, created_at, updated_at).
- [ ] Add entity `WorkflowDefinition` and `WorkflowDefinitionRepository` (JpaRepository).
- [ ] Add `WorkflowGraphValidator`: validate node types, required fields, refs (llmId, subAgentIds, etc. point to existing node ids), entry node present; return or throw validation errors.
- [ ] Unit tests for validator (valid graph, missing ref, invalid type, missing entry).

**Review**

- [ ] Can persist a workflow programmatically (e.g. in a test); validator rejects invalid graphs. No API yet.

---

### Iteration 3 — Workflow CRUD API

**Scope:** Service and controller for create, list, get, update, delete. Contract matches §2.5 sample contracts.

**Tasks**

- [ ] Implement `WorkflowDefinitionService`: create (validate then persist, return id), findAll, findById (or throw not found), update, delete.
- [ ] Implement `WorkflowController`: POST (201 + id), GET list (200 + workflows), GET by id (200 + full graph), PUT (200 + full), DELETE (204). Use DTOs; `@Valid` on create/update.
- [ ] Add `GlobalExceptionHandler` or `@ControllerAdvice`: not found → 404, validation → 400 with `{ "message", "errors" }`.
- [ ] Integration test: create workflow (story JSON from Get started), get by id, list, update, delete; assert status and body shape.

**Review**

- [ ] All five endpoints work per contract. `curl -X POST .../workflows -d @examples/story-workflow.json` returns 201 and id. List/Get/PUT/DELETE behave as specified.

---

### Iteration 4 — OpenRouter, tools, and interpreter (agent + sequence)

**Scope:** OpenRouter ChatModel factory, tool registry (time, calculator), interpreter that builds only **llm**, **agent**, and **sequence** nodes. No run API yet.

**Tasks**

- [ ] Add dependencies: langchain4j-open-ai, langchain4j-agentic (or equivalent).
- [ ] Implement `OpenRouterChatModelFactory`: build `ChatModel` from baseUrl + modelName + API key from env; fail startup if key missing (§2.4).
- [ ] Implement `ToolRegistry` and tools: `TimeTool`, `CalculatorTool` (or reuse from test project); register by id `"time"`, `"calculator"`.
- [ ] Implement `WorkflowGraphInterpreter` for **llm**, **agent**, **sequence** only: build ChatModel per llm node; build agents with agentBuilder; build sequence with sequenceBuilder; return runnable for entryNodeId when entry is sequence or agent.
- [ ] Unit tests: minimal graph (llm + 2 agents + sequence) → interpreter builds and returns runnable; tool resolution for agent with toolIds.

**Review**

- [ ] Interpreter builds a story-like graph (no HTTP run yet). Tests pass. OpenRouter key from env only.

---

### Iteration 5 — Interpreter: parallel, conditional, supervisor

**Scope:** Extend interpreter to support **parallel**, **conditional**, and **supervisor** nodes. All example flows (§2.5) are buildable.

**Tasks**

- [ ] Extend interpreter: build **parallel** (parallelBuilder, executor, output combiner), **conditional** (routerAgentId + branches → conditionalBuilder), **supervisor** (supervisorBuilder, responseStrategy).
- [ ] Handle dependency order: agents first, then sequence/parallel/conditional, then supervisor. Cache by node id.
- [ ] Unit tests: minimal graphs for parallel only, conditional only, supervisor only; interpreter builds and returns runnable.

**Review**

- [ ] All four example workflow shapes (story, evening plan, expert router, supervisor) can be built by the interpreter. No run API yet.

---

### Iteration 6 — Run API

**Scope:** POST `/api/v1/workflows/{id}/run`, request body → scope/input, invoke entry, return `{ "result": string }`. Errors → 404/400/500 with safe messages.

**Tasks**

- [ ] Add `WorkflowRunController` (or extend WorkflowController): POST `.../workflows/{id}/run`; body `RunWorkflowRequest` (e.g. Map or DTO), response `RunWorkflowResponse` (result).
- [ ] Implement `WorkflowRunService`: load workflow by id (404 if missing), run interpreter, invoke entry with request body mapped to scope/input; catch interpretation/runtime errors → 400/500, no stack trace or PII.
- [ ] Integration test: create story workflow via API, POST run with `{ "metadata": { "prompt": "...", "topic": "...", "style": "..." } }`, assert 200 and non-empty `result` (or mock LLM for deterministic test).

**Review**

- [ ] Run endpoint works for story workflow. Invalid id → 404. Invalid body or execution failure → 400/500 with `{ "message": "..." }` only.

---

### Iteration 7 — Example workflows and E2E

**Scope:** Add `examples/` with the four workflow JSONs; document and verify create + run for each (curl or test).

**Tasks**

- [ ] Add `examples/story-workflow.json`, `evening-plan-workflow.json`, `expert-router-workflow.json`, `supervisor-workflow.json` (content from §2.5).
- [ ] Add integration test or script: create each workflow, run with the prescribed input, assert response has `result` (or skip LLM call with mock).
- [ ] Update Get started or README: how to run backend and run the four examples with curl.

**Review**

- [ ] All four flows can be created and run (with real OpenRouter or mocked). Examples match sample contracts.

---

### Iteration 8 — Frontend scaffold and API client

**Scope:** **fe** as Vite + React app, wired to root Gradle; API client that calls **be** (workflows CRUD + run). Prepare for unified build (fe prod → copy into be) in a later iteration.

**Tasks**

- [ ] Bootstrap **fe** with Vite + React (latest); ensure `fe/` is the frontend module and root Gradle can run `./gradlew :fe:build` and `:fe:run` (Node plugin or Exec). **fe** prod output goes to `fe/dist/`.
- [ ] Add API client in **fe** (§7.4): `getWorkflows()`, `getWorkflow(id)`, `createWorkflow(body)`, `updateWorkflow(id, body)`, `deleteWorkflow(id)`, `runWorkflow(id, body)`. Use relative base URL in production (e.g. `''` or `'/api'`) so the same server.port serves both UI and API; use `VITE_API_BASE` for dev when **fe** runs on a different port.
- [ ] Optional: simple list page that calls `getWorkflows()` and displays names (no editor yet).

**Review**

- [ ] `./gradlew :fe:build` passes and produces `fe/dist/`. API client compiles and can be called. **be** and **fe** can run separately for dev; CORS only needed when **fe** dev server talks to **be**.

---

### Iteration 9 — Editor canvas and node types

**Scope:** React Flow canvas, node palette (LLM, Agent, Supervisor, Sequence, Parallel, Conditional), custom node components, add/connect nodes. No save/load/run yet.

**Tasks**

- [ ] Add React Flow; define `nodeTypes` and custom node components (LlmNode, AgentNode, etc.) with basic visuals.
- [ ] Implement NodePalette: drag or click to add node; each node has id, type, and data (graph fields).
- [ ] Implement edges: connect source → target; derive llmId, subAgentIds from edges when converting to graph later.
- [ ] Store nodes and edges in state; support "Set as entry" for one node (entryNodeId).
- [ ] NodeConfigPanel: when a node is selected, show form for type-specific fields (baseUrl, modelName for llm; name, llmId, outputKey, toolIds for agent; etc.).

**Review**

- [ ] Can add nodes, connect them, set entry, and see type-specific config. Graph not yet persisted.

---

### Iteration 10 — Save/load and run from UI

**Scope:** List workflows, open editor for new or existing, save (POST/PUT), load (GET → nodes/edges), run dialog (input + POST run → show result).

**Tasks**

- [ ] WorkflowList page: fetch list, show name + updatedAt; link to edit; "New" workflow; "Run" button (opens run dialog with workflow id).
- [ ] Editor: "Save" sends current graph (from nodes/edges) to create or update via API; "Load" fetches by id and converts to React Flow state (include positions if stored).
- [ ] RunDialog: form or JSON for input (`metadata.prompt`, `metadata.topic`, `metadata.style`, `metadata.mood`); submit to run API; display `result` or error.
- [ ] Conversion: `graphToReactFlow` (API response → nodes/edges), `reactFlowToGraph` (nodes/edges → API payload).

**Review**

- [ ] Can create a workflow in the UI, save it, reload the page and load it, then run it and see the result. All four example flows can be built and run from the editor (manual smoke).

---

### Iteration 11 — Polish and documentation

**Scope:** Validation feedback in UI, **unified build and single server.port**, CORS for **fe** dev, docs, full quality gates.

**Tasks**

- [ ] **Unified build:** **be** build depends on **fe** prod build and copies `fe/dist/*` into **be** static resources (e.g. `be/src/main/resources/static/` or `be/build/resources/main/static/`). Running `./gradlew :be:build` or `./gradlew build` produces a JAR that contains both backend and frontend. Root `build` may depend on `:be:build`.
- [ ] **Single server.port:** **be** serves the React app from `/` and API from `/api/v1/`. Add SPA fallback: for `GET /*` that does not start with `/api` and has no static resource, serve `index.html` (e.g. via `WebMvcConfigurer` or a controller) so client-side routes work.
- [ ] Backend: ensure graph validation returns structured errors; GlobalExceptionHandler returns consistent shape; add CORS config for **fe** dev origin (e.g. localhost:5173) when **fe** runs separately.
- [ ] Frontend: show API validation errors on save; disable Run when graph invalid or entry not set; use relative API base so prod works from same server.port.
- [ ] Docs: add or update `docs/agent-editor.md` (user guide: build once, run **be**, open http://localhost:8080 for UI and API); document workflow and run endpoints.
- [ ] Run full quality gates: `./gradlew build` (builds **fe** prod and **be**), Checkstyle (be), ESLint (fe), test coverage, no critical issues.

**Review**

- [ ] `./gradlew build` runs the full cycle (fe prod → copy → be). `./gradlew :be:bootRun` serves the app at **server.port**; opening that port shows the editor and API. All quality gates pass. CORS only for **fe** dev. Error responses never expose stack traces or PII.

---

## 4. Phase 1 — Graph model and API

### 4.1 Data model (workflow graph)

Represent a workflow as a **directed graph**: nodes = agents/workflows/LLM; edges = "feeds into" or "sub-agent of".

**Node types and payload:**

| Node type | `type` | Key fields | Notes |
|-----------|--------|------------|--------|
| LLM | `llm` | `id`, `baseUrl`, `modelName` | API key from env only; not stored. |
| Agent | `agent` | `id`, `llmId`, `name`, `outputKey?`, `toolIds[]` | Single LLM agent, optional tools. |
| Supervisor | `supervisor` | `id`, `llmId`, `subAgentIds[]`, `responseStrategy` | Picks one sub-agent. |
| Sequence | `sequence` | `id`, `subAgentIds[]` (ordered), `outputKey` | Chain: A → B → C. |
| Parallel | `parallel` | `id`, `subAgentIds[]`, `outputKey`, `threadPoolSize?`, `combiner?` | Run sub-agents in parallel; optional combiner. |
| Conditional | `conditional` | `id`, `routerAgentId`, `branches[]` (`{ conditionKey, value, agentId }`) | Router writes state; branches by condition. |

**Graph document (single workflow):**

```json
{
  "id": "uuid",
  "name": "Story workflow",
  "entryNodeId": "node-sequence-story",
  "nodes": [
    { "id": "llm-1", "type": "llm", "baseUrl": "https://openrouter.ai/api/v1", "modelName": "openai/gpt-4o-mini" },
    { "id": "agent-writer", "type": "agent", "llmId": "llm-1", "name": "CreativeWriter", "outputKey": "story" },
    { "id": "agent-editor", "type": "agent", "llmId": "llm-1", "name": "StyleEditor", "outputKey": "story" },
    { "id": "seq-story", "type": "sequence", "subAgentIds": ["agent-writer", "agent-editor"], "outputKey": "story" }
  ]
}
```

- **Entry node**: the node that is invoked when "Run" is called (e.g. a supervisor or a sequence).
- **References**: `llmId`, `subAgentIds`, `routerAgentId`, `agentId` in branches refer to other node `id`s.
- **LLM nodes:** Use OpenRouter base URL and model ID (e.g. `baseUrl: "https://openrouter.ai/api/v1"`, `modelName: "openai/gpt-4o-mini"`). API key is never stored in the graph (§2.4).

### 4.2 Persistence

- **Option A (simplest):** JPA entity `WorkflowDefinition` with `id`, `name`, `entryNodeId`, `graphJson` (JSON/JSONB), `createdAt`, `updatedAt`. Table created via Flyway.
- **Option B:** File-based (e.g. under `data/workflows/`) if you want to avoid DB schema changes at first.

Recommendation: **Option A** for consistency with the rest of the app and easier querying later.

### 4.3 REST API (Phase 1)

| Method | Path | Purpose |
|--------|------|--------|
| `POST` | `/api/v1/workflows` | Create workflow (body: graph JSON). Return `id`. |
| `GET` | `/api/v1/workflows` | List workflows (id, name, updatedAt). |
| `GET` | `/api/v1/workflows/{id}` | Get full graph by id. |
| `PUT` | `/api/v1/workflows/{id}` | Update workflow. |
| `DELETE` | `/api/v1/workflows/{id}` | Delete workflow. |

- Use DTOs for request/response; validate required fields and reference integrity (e.g. `entryNodeId` and all `*Id` refs exist in `nodes`).
- Return 400 when graph is invalid (missing refs, cycles where not allowed, etc.).

### 4.4 Tasks (Phase 1)

- [ ] Create Gradle multi-module in **agent-ui-editor**: root `settings.gradle` and `build.gradle`, **be** module with Java 25 and Spring Boot 4.0.3, **fe** module placeholder (or Vite+React scaffold).
- [ ] Define Java DTOs/records for graph in **be** (e.g. `WorkflowGraph`, `WorkflowNode`, per-type payloads).
- [ ] Add Flyway migration for `workflow_definition` table.
- [ ] Add JPA entity and repository for `WorkflowDefinition`.
- [ ] Implement validation (node types, required fields, refs, no cycles in sequence).
- [ ] Implement `WorkflowDefinitionService` (create, list, get, update, delete).
- [ ] Implement `WorkflowController` with the five endpoints above in **be**.
- [ ] Unit tests for validation and service; integration test for API.

### 4.5 Code details (Phase 1)

**Backend package layout (`be/src/main/java`):**

- `com.example.agenteditor.AgentEditorApplication` — Spring Boot entry, `@SpringBootApplication`.
- `com.example.agenteditor.domain` — `WorkflowDefinition` JPA entity: `id` (UUID), `name`, `entryNodeId`, `graphJson` (String or JSON/JSONB), `createdAt`, `updatedAt`.
- `com.example.agenteditor.repository` — `WorkflowDefinitionRepository` extends `JpaRepository<WorkflowDefinition, UUID>`.
- `com.example.agenteditor.api.v1.dto` — Request/response DTOs: `WorkflowCreateRequest` (name, entryNodeId, nodes), `WorkflowUpdateRequest`, `WorkflowResponse` (id, name, entryNodeId, nodes, createdAt, updatedAt), `WorkflowListItem` (id, name, updatedAt).
- `com.example.agenteditor.api.v1` — `WorkflowController`: `@RestController`, `@RequestMapping("/api/v1/workflows")`, inject `WorkflowDefinitionService`; five methods (POST, GET list, GET by id, PUT, DELETE); validate with `@Valid`, return ResponseEntity with DTOs.
- `com.example.agenteditor.service` — `WorkflowDefinitionService`: create (persist graph, return id), findAll (map to list DTOs), findById (or throw), update, delete; call a **graph validator** before create/update.
- `com.example.agenteditor.validation` — `WorkflowGraphValidator`: validate node types, required fields (id, type, entryNodeId present), all `llmId`/`subAgentIds`/`routerAgentId`/`agentId` refer to existing node ids, no cycles in sequence; throw or return validation errors for 400 response.

**Persistence:**

- Flyway: `be/src/main/resources/db/migration/V1__create_workflow_definition.sql` — table `workflow_definition` with columns `id` (UUID PK), `name` (VARCHAR), `entry_node_id` (VARCHAR), `graph_json` (JSONB or TEXT), `created_at`, `updated_at` (timestamps). Index on `updated_at` if listing by date.

**Configuration:**

- `be/src/main/resources/application.yml`: server port, JPA/datasource (e.g. H2 for dev), optional `openrouter.base-url`, `openrouter.model` (defaults). API key from env only.

### 4.6 Quality gates (Phase 1)

- [ ] **Build:** `./gradlew :be:build` passes (compile + test).
- [ ] **Static analysis:** Backend code style / Checkstyle (or equivalent) passes with no violations (or only agreed exceptions).
- [ ] **Tests:** All new/updated unit tests and at least one integration test for workflow CRUD pass; no flaky tests.
- [ ] **Coverage:** New code in **be** meets minimum coverage threshold (e.g. ≥ 80% line coverage for new classes) or project default.
- [ ] **API contract:** All five workflow endpoints respond with expected status and body shape for valid and invalid inputs.

---

## 5. Phase 2 — Graph interpreter

### 5.1 Responsibility

Given a **workflow graph** (from DB), build the equivalent LangChain4j objects at runtime:

- One `ChatModel` per `llm` node (or shared if you decide to single-load from env and only use `modelName` from node).
- For each `agent` node: `AgenticServices.agentBuilder(...).chatModel(...).tools(...).outputKey(...).build()`.
- For each `sequence` node: `AgenticServices.sequenceBuilder(...).subAgents(...).outputKey(...).build()`.
- For each `parallel` node: `AgenticServices.parallelBuilder(...).subAgents(...).executor(...).outputKey(...).output(combiner).build()`.
- For each `conditional` node: use `conditionalBuilder()` with branches driven by `routerAgentId` and `branches`.
- For each `supervisor` node: `AgenticServices.supervisorBuilder(...).subAgents(...).responseStrategy(...).build()`.

Agent interfaces today are Java interfaces with `@Agent` methods. For a graph-driven engine you have two options:

- **Dynamic interfaces:** generate or use a generic "invoke" interface and map graph node IDs to method names (e.g. `createStory(topic, style)` → single generic `run(scope)` that reads from scope).
- **Reflection / proxy:** build agents that implement a single generic "run" contract so the interpreter can call them without generating new Java interfaces.

Recommendation: introduce a **generic runnable contract** (e.g. "accept scope / input map, return output string") and implement thin wrappers or use LangChain4j's programmatic API if available, so the interpreter only needs to wire pre-built agent instances by ID.

### 5.2 Tool registry

- Maintain a **registry** of available tools (e.g. `TimeTool`, `CalculatorTool`) by a stable ID (e.g. `"time"`, `"calculator"`).
- When building an `agent` node, resolve `toolIds[]` to actual tool instances and pass them to `agentBuilder(...).tools(...)`.

### 5.3 Code details (Phase 2)

**OpenRouter ChatModel for graph:**

- `com.example.agenteditor.llm.OpenRouterChatModelFactory`: given an LLM node (or `baseUrl` + `modelName`), build a `ChatModel` using `dev.langchain4j.model.openai.OpenAiChatModel.builder().baseUrl(baseUrl).apiKey(apiKeyFromEnv).modelName(modelName).build()`. Read API key from `@Value("${openrouter.api-key}")` or environment; fail fast at startup if missing. Use node's `baseUrl`/`modelName` when present, else config defaults (`openrouter.base-url`, `openrouter.model`).
- Ensure only one API key is used (env/config); never from graph.

**Interpreter and tools:**

- `com.example.agenteditor.interpreter.WorkflowGraphInterpreter`: constructor or factory takes `WorkflowGraph`, `OpenRouterChatModelFactory`, `ToolRegistry`. Method `buildEntryRunnable(WorkflowGraph graph)` or similar: resolve nodes in dependency order (agents → sequences/parallel/conditional → supervisor); for each `llm` node, get `ChatModel` from factory; for each `agent` node build with `AgenticServices.agentBuilder(...).chatModel(chatModel).tools(resolveTools(node.getToolIds())).outputKey(node.getOutputKey()).build()`; for sequence/parallel/conditional/supervisor use corresponding `AgenticServices.*Builder`. Cache built runnables by node id. Return the runnable for `entryNodeId`.
- `com.example.agenteditor.tools.ToolRegistry`: interface `Object[] getTools(List<String> toolIds)`; implementation maps `"time"` → `TimeTool`, `"calculator"` → `CalculatorTool` (or equivalent). Register as Spring bean; tools can be `@Component` with `@Tool` methods (LangChain4j).
- `com.example.agenteditor.tools.TimeTool`, `CalculatorTool`: same contract as test project (e.g. return current time UTC, evaluate arithmetic expression); place in `be` or reuse from a shared lib.

**Package layout:**

- `com.example.agenteditor.llm` — OpenRouterChatModelFactory.
- `com.example.agenteditor.interpreter` — WorkflowGraphInterpreter, and any generic runnable adapter for LangChain4j workflow/agent interfaces.
- `com.example.agenteditor.tools` — ToolRegistry, TimeTool, CalculatorTool.

**Dependencies (`be/build.gradle`):**

- `langchain4j-open-ai`, `langchain4j-agentic` (or equivalent for agentic APIs); Spring Boot starters (web, data-jpa, validation). Use same LangChain4j BOM/versions as test project where applicable.

### 5.4 Tasks (Phase 2)

- [ ] Implement `WorkflowGraphInterpreter` (or similar) that:
  - Takes a `WorkflowGraph` and a `ChatModel` (or builds one per `llm` node from env + node's `modelName`).
  - Builds agents in dependency order (agents first, then sequences/parallel/conditional, then supervisor).
  - Caches built instances by node id for the lifetime of the graph execution.
- [ ] Implement tool registry (map `toolId` → bean or instance).
- [ ] Handle conditional branches (router output key and value; map to LangChain4j conditionalBuilder).
- [ ] Unit tests in **be**: feed minimal graphs (one agent, one sequence, one parallel, one conditional) and assert that the interpreter builds without throwing and returns runnable objects.

### 5.5 Quality gates (Phase 2)

- [ ] **Build:** `./gradlew :be:build` passes.
- [ ] **Static analysis:** Backend Checkstyle (or equivalent) passes.
- [ ] **Interpreter tests:** Unit tests for each node type (agent, sequence, parallel, conditional) pass; tool registry resolution tested.
- [ ] **Coverage:** Interpreter and tool registry code meets coverage threshold.
- [ ] **No regressions:** Phase 1 integration tests still pass.

---

## 6. Phase 3 — Run API

### 6.1 Contract

- **Request:** `POST /api/v1/workflows/{id}/run`  
  Body: JSON with input parameters (e.g. `{ "metadata": { "prompt": "hello" } }`, or `{ "metadata": { "prompt": "Write a noir story", "topic": "robots", "style": "noir" } }`).
- **Response:** 200 + body with output (e.g. `{ "result": "..." }`) or streamed response if you add SSE later.
- **Behavior:** Load workflow by `id`, run interpreter to get runnable entry node, invoke with request body mapped to scope/input, return result.

### 6.2 Input mapping

- Map request body into LangChain4j scope state. Use `metadata.prompt` as the primary user prompt; keep extra keys (e.g. `metadata.topic`, `metadata.style`, `metadata.mood`) as optional context in the same JSON object.

### 6.3 Tasks (Phase 3)

- [ ] Add `POST /api/v1/workflows/{id}/run` in `WorkflowController` (or a dedicated `WorkflowRunController`).
- [ ] Implement run flow: load graph → interpret → invoke entry node with request body.
- [ ] Define request/response DTOs; handle errors (workflow not found, interpretation error, runtime error) with appropriate status codes and messages.
- [ ] Integration test in **be**: create a workflow (e.g. story-like sequence) via API, then run it and assert response shape and non-empty result.

### 6.4 Code details (Phase 3)

**Run API:**

- `com.example.agenteditor.api.v1.WorkflowRunController` (or add to `WorkflowController`): `POST /api/v1/workflows/{id}/run`. Request body: `RunWorkflowRequest` — JSON object (e.g. `Map<String, Object>` or typed DTO with `metadata` fields such as `prompt`, `topic`, `style`, etc.). Response: `RunWorkflowResponse` — `result` (String) or `{ "result": "..." }`.
- `com.example.agenteditor.service.WorkflowRunService`: load `WorkflowDefinition` by id (or throw 404); call `WorkflowGraphInterpreter` to build entry runnable; invoke with request body mapped to scope/input (e.g. put request key-value pairs into LangChain4j scope or pass as method args); return result string. Catch interpretation/runtime errors and map to 400/500 with safe message (no stack trace, no PII).
- DTOs: `RunWorkflowRequest` (flexible map or fields like `metadata.prompt`, `metadata.topic`, `metadata.style`), `RunWorkflowResponse` (record or class with `String result`).

**Error handling:**

- Use `@ControllerAdvice` / `GlobalExceptionHandler` in `com.example.agenteditor.api`: map `WorkflowNotFoundException` → 404, validation/interpretation errors → 400, execution errors → 500; response body `{ "message": "..." }` only.

**OpenRouter in run flow:**

- The interpreter uses `OpenRouterChatModelFactory` to obtain a `ChatModel` per `llm` node when building the graph; no extra run-specific config. Ensure API key is available in the environment when running workflows.

### 6.5 Quality gates (Phase 3)

- [ ] **Build:** `./gradlew :be:build` passes.
- [ ] **Run API test:** Integration test creates a workflow via API and runs it successfully; response matches contract.
- [ ] **Error handling:** Run endpoint returns appropriate status (404, 400, 500) and safe messages for missing workflow, invalid input, and execution failure; no stack traces or PII in responses.
- [ ] **No regressions:** Phase 1 and Phase 2 tests still pass.

---

## 7. Phase 4 — Frontend editor

The frontend lives in the **fe** module: React (latest stable) + Vite (latest stable), as per §2.1.

### 7.1 Stack (fe module)

- **Build / dev:** [Vite](https://vite.dev/) — latest stable.
- **Framework:** [React](https://react.dev/) — latest stable (e.g. React 19.x).
- **Flow canvas:** [React Flow](https://reactflow.dev/) — nodes and edges, drag-and-drop, zoom/pan.
- **UI:** Simple forms in node panels or side panel (Tailwind or plain CSS).
- **HTTP:** Fetch or axios to the **be** API (`/api/v1/workflows`, `/api/v1/workflows/{id}/run`).

### 7.2 Frontend structure

- **Pages/views:**
  - List: list workflows (from `GET /api/v1/workflows`), link to edit, button to create new, button to run (with simple input modal or page).
  - Editor: canvas with node palette (LLM, Agent, Supervisor, Sequence, Parallel, Conditional); drag node onto canvas; click node to open config form; connect nodes with edges (source → target) to define `subAgentIds` / `llmId` / `routerAgentId` / branches.
- **State:** Store graph in React state; on save, send `PUT` or `POST` to backend. On load, fetch graph and convert to React Flow nodes/edges.
- **Run:** From list or editor, "Run" opens a dialog with JSON or form fields for input; on submit, `POST /api/v1/workflows/{id}/run` and show result.

### 7.3 Mapping UI ↔ graph

- **Nodes:** Each canvas node has type and data; when saving, convert to the JSON node format (id, type, ...). When loading, convert JSON nodes to React Flow nodes (position can be stored in node payload or in a separate `viewport` field).
- **Edges:** Edges define "feeds into" or "is sub-agent of". When saving, derive `subAgentIds`, `llmId`, etc. from edges (e.g. edges from LLM to Agent set `agent.llmId = source`; edges from Agent to Sequence set `sequence.subAgentIds` order by edge order or position).
- **Entry node:** Mark one node as "entry" (e.g. checkbox or right-click "Set as entry"); store as `entryNodeId`.

### 7.4 Code details (Phase 4)

**Frontend layout (`fe/src`):**

- `fe/src/main.jsx` (or `main.tsx`) — entry; mount React app, router if used.
- `fe/src/App.jsx` — top-level routes: e.g. `/` (list), `/workflows/:id/edit` (editor), optional `/workflows/new`.
- `fe/src/api/` — API client: `getWorkflows()`, `getWorkflow(id)`, `createWorkflow(body)`, `updateWorkflow(id, body)`, `deleteWorkflow(id)`, `runWorkflow(id, body)`. In production (served from same server.port), use relative base URL (e.g. `''` or `'/api'`); for **fe** dev server use `VITE_API_BASE` (e.g. `http://localhost:8080`).
- `fe/src/components/` — `WorkflowList` (table or list, link to edit, "New", "Run" button), `WorkflowEditor` (React Flow canvas + palette + save), `NodePalette` (drag LLM, Agent, Supervisor, Sequence, Parallel, Conditional), `NodeConfigPanel` (form for selected node: type-specific fields), `RunDialog` (input fields or JSON, submit to run API, show result).
- `fe/src/nodes/` — React Flow custom node components: `LlmNode`, `AgentNode`, `SupervisorNode`, `SequenceNode`, `ParallelNode`, `ConditionalNode`; each receives `data` (node payload) and optional `selected` for config panel.
- `fe/src/hooks/` or `fe/src/utils/` — `graphToReactFlow(nodes, edges)`, `reactFlowToGraph(nodes, edges)` to convert between backend graph format and React Flow state; include `position` in node data or separate viewport state.

**React Flow:**

- Use `reactflow` package; `<ReactFlow nodes={nodes} onNodesChange={...} edges={edges} onEdgesChange={...} nodeTypes={nodeTypes} />`. `nodeTypes`: map type string to custom component (e.g. `llm` → LlmNode). Store `id`, `type`, and graph fields in `node.data`.

**LLM node in UI:**

- In node config form for `llm`, show `baseUrl` (default `https://openrouter.ai/api/v1`) and `modelName` (default e.g. `openai/gpt-4o-mini`); do not show or store API key (handled by backend env).

### 7.5 Tasks (Phase 4)

- [ ] Bootstrap **fe** with Vite + React (latest versions): `fe/` as the frontend module in agent-ui-editor; wire `fe` into root Gradle (e.g. Node plugin or Exec) so `./gradlew :fe:build` and `:fe:run` work.
- [ ] Add React Flow in **fe**; define node types (LLM, Agent, Supervisor, Sequence, Parallel, Conditional) with basic visuals.
- [ ] Implement "add node" from palette; node config form (fields per type); "connect" via edges.
- [ ] Implement save (POST/PUT workflow); load (GET workflow → set nodes/edges) in **fe** against **be** API.
- [ ] Implement list view and "Run" (run dialog + call run API, show result).
- [ ] Store node positions in graph so layout is preserved.
- [ ] Optional: validate graph on save (entry set, refs valid) and show errors in UI.

### 7.6 Quality gates (Phase 4)

- [ ] **Build:** `./gradlew :fe:build` passes (frontend builds without errors).
- [ ] **Lint:** Frontend ESLint (or equivalent) passes with no errors; fix or document any agreed exceptions.
- [ ] **E2E / smoke:** Can open editor, add a node, save workflow, and run it (or manual smoke test signed off); **fe** can call **be** API successfully.
- [ ] **No regressions:** Backend `./gradlew :be:build` still passes.

---

## 8. Phase 5 — Polish and documentation

### 8.1 Validation and errors

- **Backend:** Validate graph on create/update (all refs exist, entry node present, no invalid cycles). Return 400 with clear messages (e.g. "Missing node id: agent-writer").
- **Frontend:** Disable "Run" or "Save" when graph is invalid; show validation errors from API or client-side checks.
- **Run errors:** Catch interpretation and execution errors; return 500 or 422 with a safe message (no stack trace, no PII).

### 8.2 Testing

- **Backend:** Unit tests for graph validation, interpreter (per node type), tool registry. Integration tests for workflow CRUD and run API (at least one workflow type: e.g. sequence).
- **Frontend:** Optional; at least smoke test: load editor, add node, save, run.

### 8.3 Documentation

- **User:** Short guide in `docs/` (e.g. `docs/agent-editor.md`): how to open the editor, add nodes, connect them, set entry, save, run. Screenshots or diagrams help.
- **Developer:** In this plan or in `docs/architecture.md`, describe the graph model, interpreter, and API so future changes are clear.
- **API:** Document new endpoints in `docs/api.md` (or OpenAPI); keep request/response examples for workflows and run.

### 8.4 Tasks (Phase 5)

- [ ] Add graph validation (refs, entry, cycles) and consistent error responses.
- [ ] Add/expand unit and integration tests as above.
- [ ] Write `docs/agent-editor.md` (user-facing) and update architecture/API docs.
- [ ] Unified build: **be** build runs **fe** prod and copies into **be** static resources; **be** serves the app at **server.port** with SPA fallback. CORS in **be** only for **fe** dev server when needed.

### 8.5 Code details (Phase 5)

**Backend validation:**

- Reuse or extend `WorkflowGraphValidator` (§4.5): on create/update, validate all refs, entry node present, no invalid cycles; return structured validation errors (e.g. list of field + message). Controller returns 400 with `{ "message": "...", "errors": [...] }` if present.

**Unified build and single server.port (required):**

- **be** build must run **fe** prod build and copy `fe/dist/*` into **be** static resources (§2.3). Gradle: **be** has a task (e.g. `copyFeDist`) that depends on **fe** build and copies into `be/src/main/resources/static/` or `be/build/resources/main/static/`; `processResources` depends on it so the JAR contains the React app.
- **be** serves the SPA from `/` and API from `/api/v1/`. Implement SPA fallback: for requests that do not start with `/api` and for which no static file exists, serve `index.html` (e.g. `ResourceHttpRequestHandler` customization or a `@Controller` that returns the resource). Then `http://localhost:${server.port}` serves both UI and API.

**CORS:**

- In **be**, add CORS config only for **fe** dev server (e.g. `http://localhost:5173`) when running **fe** with `npm run dev`; for production, same-origin so no CORS needed.

**Documentation paths:**

- `docs/agent-editor.md` — user guide (in repo root or under `agent-ui-editor/`).
- Update `docs/api.md` or OpenAPI spec with workflow CRUD and run endpoints; include example request/response and note that LLM is OpenRouter (API key from env).

### 8.6 Quality gates (Phase 5)

- [ ] **Full build:** `./gradlew build` passes for both **fe** and **be**.
- [ ] **Static analysis:** Checkstyle (be) and ESLint (fe) pass; no new critical or high issues.
- [ ] **Test suite:** All unit and integration tests pass; coverage thresholds met for **be** (and **fe** if defined).
- [ ] **Documentation:** User-facing guide (e.g. `docs/agent-editor.md`) and API/architecture updates are in place and reviewed.
- [ ] **Definition of done:** Validation, error responses, unified build (fe prod → be static), and single server.port (UI + API from same port) are implemented and verified.

---

## 9. Dependency order

```
Phase 1 (model + API)  →  Phase 2 (interpreter)  →  Phase 3 (run API)
                                                          ↓
Phase 4 (frontend)  ←─────────────────────────────────────┘
        ↓
Phase 5 (polish + docs)
```

Phase 4 can start once Phase 3 is done (you can drive the run API from Postman/curl first). Phase 5 can be done incrementally (e.g. validation in Phase 1, tests alongside 2–3, docs at the end). The Gradle multi-module and **be**/ **fe** setup should be in place at the start of Phase 1 (§2).

---

## 10. Risks and mitigations

| Risk | Mitigation |
|------|------------|
| LangChain4j agents are interface-based; dynamic graphs don't match 1:1 | Use a generic "runnable" abstraction and a single invoke entry point per entry node type (sequence/parallel/supervisor/agent). |
| Complex conditionals (router + branches) | Implement one branch type first (e.g. category enum → agent); generalize once stable. |
| Frontend complexity (many node types) | Start with Agent + Sequence + LLM only; add Supervisor, Parallel, Conditional in follow-up iterations. |
| Security (running arbitrary graphs) | Keep run API behind auth; validate graph size and depth to avoid DoS; never execute user code from the graph. |

---

## 11. Success criteria

- User can create a workflow in the UI that matches the current "Story" flow (sequence of two agents) and run it with `metadata.prompt` (and optional `metadata.topic`/`metadata.style`) to get a story.
- User can list, load, edit, and save workflows via the API and the UI.
- All existing agent configurations (single agent, supervisor, sequential, parallel, conditional) are representable in the graph and runnable via the interpreter.
- Documentation and tests allow a new developer to add a new node type or tool and wire it through.

---

*Document version: 1.5. Unified build and single server.port (§2.3): Java build generates React prod, copies to **be** static, server serves UI and API from server.port. Update this plan as you implement (e.g. link to tickets, adjust phases).*
