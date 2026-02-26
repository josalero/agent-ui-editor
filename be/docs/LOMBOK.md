# Lombok in the Backend

The **be** module uses [Lombok](https://projectlombok.org/) to reduce boilerplate (getters, constructors, etc.) while keeping the codebase consistent and documented.

## Dependency

Lombok is declared in `be/build.gradle` as:

- **compileOnly** and **annotationProcessor** (main code)
- **testCompileOnly** and **testAnnotationProcessor** (tests)

It is not packaged into the runtime JAR; the annotation processor runs at compile time only.

## Conventions

| Use case | Annotation | Where |
|----------|------------|--------|
| Immutable entity/DTO getters | `@Getter` | e.g. `WorkflowDefinition`, exception classes |
| JPA no-arg constructor | `@NoArgsConstructor(access = AccessLevel.PROTECTED)` | Entities only |
| Constructor injection | `@RequiredArgsConstructor` | Services, controllers (replaces explicit constructor for `final` fields) |

## What we do *not* use Lombok for

- **Records** — Request/response DTOs and small value types remain Java records (`WorkflowCreateRequest`, `WorkflowResponse`, `ValidationError`, etc.). Records already provide accessors and constructors.
- **Builders** — No `@Builder` on entities so far; we use full constructors with null checks where needed.
- **Logging** — Can add `@Slf4j` later if we introduce logging in new classes.

## Classes using Lombok

- **WorkflowDefinition** — `@Getter`, `@NoArgsConstructor(access = PROTECTED)`; public all-args constructor kept for validation.
- **WorkflowController** — `@RequiredArgsConstructor` for `WorkflowDefinitionService`.
- **WorkflowDefinitionService** — `@RequiredArgsConstructor` for repository and `ObjectMapper`.
- **WorkflowNotFoundException** — `@Getter` for `workflowId`.
- **WorkflowGraphValidationException** — `@Getter` for `errors`; constructor kept for custom list copy.

## IDE setup

IDEs need the Lombok plugin so that generated code (getters, constructors) is recognized and autocomplete works:

- **IntelliJ IDEA:** [Lombok plugin](https://plugins.jetbrains.com/plugin/6317-lombok) (often bundled).
- **Eclipse:** Run `lombok.jar` as an installer or add Lombok to the classpath.
- **VS Code:** Use a Java extension that supports Lombok (e.g. Language Support for Java by Red Hat with Lombok support).

Enable "Annotation Processing" in your IDE so that the Lombok annotation processor runs (Gradle already does this for the build).
