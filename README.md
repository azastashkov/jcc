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

## Quick Start

### 1. Build the binary

```bash
./gradlew :cli:installDist
# Produces cli/build/install/jcc/bin/jcc (requires a system JDK 21 on PATH)
```

For a self-contained distribution that includes its own trimmed JRE:

```bash
./gradlew :cli:jlinkImage
# Produces cli/build/jlink-image/bin/jcc (~55 MB, runs without a system JDK)
```

From here on, either launcher works — pick one and export it:

```bash
export JCC=$PWD/cli/build/install/jcc/bin/jcc
# or
export JCC=$PWD/cli/build/jlink-image/bin/jcc
```

### 2. Pick a provider

**Option A: Anthropic (Claude).**

```bash
export ANTHROPIC_API_KEY=sk-ant-...
"$JCC" prompt --model opus "Explain Java sealed interfaces in two sentences."
```

Model aliases: `opus` → `claude-opus-4-7`, `sonnet` → `claude-sonnet-4-6`,
`haiku` → `claude-haiku-4-5`. Pass a full model id to override.

**Option B: Local Ollama (Qwen3-Coder, Llama, etc.) via the OpenAI-compat adapter.**

```bash
brew install ollama                             # if needed
ollama serve &                                  # listens on 127.0.0.1:11434
ollama pull qwen3-coder:30b-a3b-fp16            # 61 GB; ~5–30 min depending on bandwidth

export OPENAI_BASE_URL=http://localhost:11434/v1
export OPENAI_API_KEY=ollama                    # any non-empty value
unset ANTHROPIC_API_KEY                         # optional; OPENAI_BASE_URL takes precedence anyway

"$JCC" prompt --model qwen3-coder:30b-a3b-fp16 \
  --max-tokens 300 \
  --permission-mode read-only \
  "Write a regex that matches ISO-8601 timestamps."
```

The same env vars work against any OpenAI-compatible endpoint: xAI's
Grok, LocalAI, vLLM, LM Studio, OpenRouter, etc. `test-qwen.sh` at the
repo root reproduces the full Ollama smoke-test sequence.

### 3. Interactive REPL

Running `jcc` with no subcommand drops into the REPL:

```
$ "$JCC" --model qwen3-coder:30b-a3b-fp16 --permission-mode workspace-write
jcc REPL — type /help for commands, /exit to quit.
Session: session-1777245891123-abc1  model=qwen3-coder:30b-a3b-fp16

jcc> /status
model:        qwen3-coder:30b-a3b-fp16
session:      session-1777245891123-abc1
messages:     0
permissions:  workspace-write

jcc> refactor the loop in src/main/java/Foo.java to use streams
... streaming response with tool calls ...

jcc> /cost
tokens: input=1240 output=312 cache_read=0 cache_write=0 total=1552

jcc> /exit
```

### 4. Resume a prior session

Every invocation writes to a JSONL file under
`~/.local/share/jcc/sessions/`. Continue a previous conversation with:

```bash
"$JCC" --resume latest                          # pick up the newest session
"$JCC" --resume session-1777245891123-abc1      # or name one explicitly
"$JCC" prompt --resume latest "and now add a unit test"
```

### 5. Project-level defaults

Drop a `.jcc.json` at your repo root so you don't have to retype flags:

```json
{
  "model": "qwen3-coder:30b-a3b-fp16",
  "max_tokens": 4096,
  "permissions": {
    "default_mode": "workspace-write",
    "allow": ["read_file", "glob", "grep"]
  },
  "mcp": {
    "fs": {
      "type": "stdio",
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-filesystem", "/Users/me/code"]
    }
  }
}
```

With that file present, `jcc prompt "..."` and `jcc` (REPL) both pick
up the model, permissions, and MCP servers automatically.

## Requirements

- **Java 21+** (uses records, sealed interfaces, pattern matching, virtual threads)
- **Gradle 8.10+** (or just use the bundled `./gradlew`)
- One of:
  - `ANTHROPIC_API_KEY` for Anthropic, **or**
  - `OPENAI_BASE_URL` + `OPENAI_API_KEY` for an OpenAI-compatible endpoint
- macOS / Linux primary targets; Windows builds but is not routinely exercised

## Architecture

Six Gradle modules, with dependencies flowing strictly downward:

```
core  ◄───  api  ◄───  runtime  ◄───  tools       ◄───  cli
                                 ◄───  commands   ◄──┘
```

| Module     | Package root           | Purpose                                                                                                          |
| ---------- | ---------------------- | ---------------------------------------------------------------------------------------------------------------- |
| `core`     | `io.jcc.core`          | Shared DTOs, sealed `ContentBlock` hierarchy, `Usage` record, `MessageRole`, `Result<T,E>`, Jackson-configured `ObjectMapper`, virtual-thread + scheduled executors |
| `api`      | `io.jcc.api`           | `ProviderClient` interface plus `AnthropicProviderClient` (native `/v1/messages` SSE) and `OpenAiCompatProviderClient` (`/v1/chat/completions` translation). `SseLineParser`, `MessageRequest` / `StreamEvent` / `ContentBlockDelta` sealed hierarchies. |
| `runtime`  | `io.jcc.runtime`       | Session JSONL persistence (256 KB rotation, 3 rotated files max), four-scope `ConfigLoader`, `PermissionPolicy` (5 modes), `ConversationRuntime` tool loop, `McpStdioClient` + `McpServerManager`, `SubagentExecutor` + `TaskRegistry`, `CompositeToolExecutor`, `FilteringToolExecutor` |
| `tools`    | `io.jcc.tools`         | Eight built-ins (`BashTool`, `ReadFileTool`, `WriteFileTool`, `EditFileTool`, `GlobTool`, `GrepTool`, `WebFetchTool`, `WebSearchTool`), plus `AgentTool` for sub-agent delegation. `BuiltinToolRegistry` implements `ToolExecutor`. |
| `commands` | `io.jcc.commands`      | `SlashCommand` interface + registry + handlers: `/help`, `/status`, `/cost`, `/clear`, `/compact`, `/config`, `/mcp`, `/skills`, `/subagent`, `/exit` |
| `cli`      | `io.jcc.cli`           | Picocli `JccCommand` root + `PromptSubcommand`, JLine 3 `ReplSession`, `TextRenderer` / `JsonRenderer` behind `StreamingRenderer`, `RuntimeEnvironment` bootstrap, `Main` |

**Concurrency.** Virtual threads (Project Loom) handle every blocking
I/O path: HTTP requests, tool invocations, MCP subprocess stdout / stderr
readers, sub-agent turns. A two-thread `ScheduledExecutorService` handles
timeouts only. `ReentrantLock` is used around any blocking-I/O critical
section (MCP stdin writes, session appends) to avoid Loom pinning.

**Error handling.** Infrastructure failures raise unchecked exceptions
(`ApiException`, `ProviderException`, `ConfigException`,
`McpTransportException`, `SessionException`). Tool execution uses
`Result<ToolOutput, ToolError>` because tool failures are a normal part
of the agent loop and surface as `is_error: true` tool-result blocks
rather than thrown.

**JSON.** A single configured Jackson `ObjectMapper` (SNAKE_CASE naming,
parameter-names + jdk8 + jsr310 modules, `FAIL_ON_UNKNOWN_PROPERTIES=false`).
Polymorphic wire types use `@JsonTypeInfo(use=NAME, property="type")` +
`@JsonSubTypes` on sealed interfaces with records per variant.

## Commands

**CLI flags** (apply to both the root and the `prompt` subcommand):

| Flag                           | Meaning                                                                                 |
| ------------------------------ | --------------------------------------------------------------------------------------- |
| `--model <name>`               | Model name or alias (`opus`, `sonnet`, `haiku`, or any provider-specific id)            |
| `--max-tokens <n>`             | Response token cap (default 1024; falls back to `.jcc.json` `max_tokens`)               |
| `--output-format text\|json`   | `text` streams prose to stdout; `json` emits NDJSON assistant events                    |
| `--permission-mode <mode>`     | `read-only`, `workspace-write`, `danger-full-access`, `prompt`, `allow`                 |
| `--resume <ref>`               | Reload a session: session id, filename, `latest`, or an explicit `.jsonl` path          |

**Slash commands** (REPL):

| Command              | Purpose                                                              |
| -------------------- | -------------------------------------------------------------------- |
| `/help`              | List registered commands                                             |
| `/status`            | Current model, session id, permission mode, message count            |
| `/cost`              | Accumulated token usage for the current REPL session                 |
| `/clear`             | Drop the in-memory conversation history (session file is preserved)  |
| `/compact`           | (Stub) Summarize and trim history — real implementation pending      |
| `/config`            | Show the merged `RuntimeConfig` as pretty JSON                       |
| `/mcp`               | List configured MCP servers and their tools                          |
| `/skills`            | (Stub) Skills discovery                                              |
| `/subagent [list\|stop <id>]` | Inspect or cancel running sub-agents                         |
| `/exit`              | Quit the REPL                                                        |

## Tools

Eight built-in tools, registered by default and gated by
`PermissionPolicy`:

| Name          | Minimum mode        | Purpose                                                |
| ------------- | ------------------- | ------------------------------------------------------ |
| `read_file`   | `read-only`         | Read a UTF-8 file (1 MB cap, optional offset/limit)    |
| `glob`        | `read-only`         | Match files by pattern, up to 500 paths, sorted mtime-desc |
| `grep`        | `read-only`         | Regex search over file contents                        |
| `web_fetch`   | `read-only`         | HTTP(S) GET up to 5 MB                                 |
| `web_search`  | `read-only`         | Stub — returns a "not implemented" error               |
| `bash`        | `workspace-write`   | `/bin/sh -c` with 120 s default timeout, 64 KB output cap |
| `write_file`  | `workspace-write`   | Write text; creates parent directories                 |
| `edit_file`   | `workspace-write`   | Unique-match find-and-replace (or `replace_all`)       |
| `Agent`       | `danger-full-access` | Spawn a sub-agent (restricted tool allowlist per type) |

MCP tools are surfaced additionally with the name
`mcp__<server>__<tool_name>` and require `workspace-write` by default.

## Configuration

`ConfigLoader` deep-merges JSON from four scopes (later layers override
earlier ones):

1. `~/.claude/settings.json` — user-scope baseline (kept compatible with
   upstream Claude configs)
2. `<project>/.jcc.json` — project defaults (model, max_tokens, MCP servers)
3. `<project>/.jcc/settings.json` — project-settings split
4. `<project>/.jcc/settings.local.json` — machine-local overrides (should be `.gitignore`d)

Most keys **replace** on conflict; `permissions.allow`,
`permissions.deny`, and `permissions.ask` arrays are **additive with
dedup**.

## Building and testing

```bash
./gradlew build                 # compile + test everything (58 tests)
./gradlew test                  # just run the test suite
./gradlew :runtime:test         # run a single module's tests
./gradlew :cli:installDist      # → cli/build/install/jcc/bin/jcc
./gradlew :cli:jlinkImage       # → cli/build/jlink-image/bin/jcc (bundled JRE)
```

All tests are unit or integration-with-embedded-server (HTTP SSE via
`com.sun.net.httpserver`, MCP via a bash-scripted mock) — no external
network needed to `./gradlew build`.

## Reference

`jcc` ports the MVP surface of the Rust
[`claw-code`](https://github.com/ultraworkers/claw-code) reference
implementation. Design decisions, the milestone plan (M1 – M7), the Rust
files each Java class maps to, and the explicit list of deferred items
are captured in the original planning document committed alongside this
repo's early history.
