# Agent UI Editor

Planning and implementation for an n8n-like visual editor for agent workflows.

- **[agent-editor-plan.md](agent-editor-plan.md)** — Full implementation plan (phases, data model, API, frontend, risks, iterations).

The editor is intended to work with the test project's LangChain4j agentic setup: design workflows as graphs, persist them, and run them via API. **Do not put passwords or API keys in `application.yml` or documentation**—use environment variables (e.g. `OPENROUTER_API_KEY`) or a secret manager.

## Quick start (after Iteration 1)

```bash
# Build everything: fe is built, then copied into be static resources; one JAR serves both
./gradlew build

# Run the backend (ensure port 8080 is free)
./gradlew :be:bootRun
```

- **API:** http://localhost:8080/api/v1/health  
- **UI (embedded):** http://localhost:8080/ (serves **fe** from the same server.port)

The **be** build depends on **:fe:build** and copies `fe/dist/*` into `be/build/resources/main/static/` via the `copyFeToStatic` Gradle task, so the runnable JAR contains both backend and frontend. The **fe** module is a placeholder until Iteration 8 (Vite+React).

## Backend (be) tech notes

- **Lombok** is used for getters, no-arg constructors (JPA), and constructor injection. See **[be/docs/LOMBOK.md](be/docs/LOMBOK.md)** for conventions and which classes use it.
- **Records** are used for DTOs and small value types; **entities** remain classes with Lombok where appropriate.
- **Jackson 3** (Spring Boot 4): JSON is handled with `tools.jackson.databind.json.JsonMapper` (not Jackson 2’s `ObjectMapper`). Annotations stay in `com.fasterxml.jackson.annotation`. Exceptions are unchecked (`tools.jackson.core.JacksonException`).
