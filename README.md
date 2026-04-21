# jcc

Java 21 reimplementation of the `claw-code` CLI agent harness. Scoped to MVP + MCP + REPL + multi-provider.

## Status

**M1 – M7 complete; 58 tests green.** The MVP feature set is in place:
Picocli CLI and JLine 3 REPL with slash commands; streaming text / NDJSON
output; Anthropic and OpenAI-compatible providers behind one interface;
eight built-in tools (bash, read/write/edit_file, glob, grep, web_fetch,
web_search); five-mode permission policy; JSONL session persistence with
256 KB rotation and `--resume`; four-scope config loader; JSON-RPC 2.0
MCP stdio client with server manager; in-process sub-agent spawning via
the `Agent` tool with per-type tool allowlists and a `TaskRegistry`; and
a jlink runtime image.

**Verified** end-to-end against the live Anthropic API and against a
local Ollama server hosting `qwen3-coder:30b-a3b-fp16` (FP16 MoE, 61 GB,
fits comfortably on a 128 GB Mac). `test-qwen.sh` reproduces the Ollama
run.

**Known deferred items** (follow-ups, not blockers):
- OpenAI-compat provider does not yet translate `tool_calls` — tool use
  through Ollama / xAI / LocalAI is text-only.
- `/compact` is a stub (real summarization + session rewrite pending).
- `web_search` is a stub — use `web_fetch` against a known URL instead.
- Ollama's streaming chunks don't carry token usage, so `/cost` shows
  zero when using the OpenAI-compat route.
- No per-block `cache_control` markers yet (Anthropic prompt-caching
  beta header is sent; per-message `Usage` isn't persisted to session
  JSONL).
- No cross-process `FileChannel.tryLock()` on `Session.append` yet.

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
