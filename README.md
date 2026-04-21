# cc-java

Java 21 reimplementation of the `claw-code` CLI agent harness. Scoped to MVP + MCP + REPL + multi-provider.

## Status

**M1 in progress.** See the implementation plan in the repository issue tracker or planning doc.

## Requirements

- Java 21+
- Gradle 8.10+ (or use the included wrapper)
- `ANTHROPIC_API_KEY` set in the environment for the Anthropic provider

## Build

```
./gradlew build
```

## Run

```
./gradlew :cli:run --args="prompt 'hello'"
```

## Layout

| Module     | Purpose                                                          |
| ---------- | ---------------------------------------------------------------- |
| `core`     | Shared DTOs, sealed content-block hierarchy, Jackson, concurrency |
| `api`      | Provider clients (Anthropic + OpenAI-compat), SSE parsing         |
| `cli`      | Picocli root, subcommands, streaming renderer, `Main` entrypoint |

Later milestones will add `runtime`, `tools`, and `commands`.
