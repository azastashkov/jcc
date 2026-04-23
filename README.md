# jcc

The jcc project is a Java reimplementation of the `claw-code` CLI agent harness, designed for educational purposes. It's a powerful interactive command-line interface that allows users to:

## Core Functionality
- Communicate with language models (Anthropic Claude or OpenAI-compatible LLMs like Ollama)
- Execute local file system and shell operations through built-in tools
- Manage conversations with session persistence and resumption
- Work in both interactive REPL mode and one-shot prompt mode

## Main Use Cases
1. **Interactive Development Assistant**: Real-time code analysis, refactoring, and explanation
2. **Local Automation**: File operations, shell command execution, system interaction
3. **Educational Tool**: Understanding language model architectures, agent design patterns
4. **Research**: Experimenting with different LLM providers and prompting strategies

## Key Features
- **Multi-model Support**: Works with both Anthropic (Claude) and OpenAI-compatible endpoints
- **Comprehensive Tool System**: 8 built-in tools including file operations, shell commands, and web requests
- **Advanced Permission System**: 5 security modes (read-only, workspace-write, etc.) with fine-grained control
- **Session Management**: Resume previous conversations, persist history to JSONL files
- **MCP Integration**: Connect with Model Context Protocol servers for extended functionality
- **Sub-agent Support**: Delegate complex tasks to specialized agents

## Architecture
The project is modularized into six layers (from core to CLI):
- **core**: Shared DTOs, records, usage tracking, virtual threads
- **api**: Provider clients for Anthropic and OpenAI-compat, SSE parsing
- **runtime**: Session handling, tool execution, permission policies
- **tools**: Built-in tools (read, write, bash, grep, etc.) and AgentTool
- **commands**: REPL slash commands (/help, /status, /cost, etc.)
- **cli**: Main command entry point, REPL interface, JSON rendering

## Available Tools
- `read_file`, `write_file`, `edit_file` - File operations
- `bash` - Shell command execution
- `glob`, `grep` - File system and content search
- `web_fetch` - HTTP(S) requests
- `Agent` - Sub-agent delegation

This is an educational implementation that demonstrates:
- Language model agent architecture
- Tool calling mechanisms with permission controls
- Interactive CLI design with virtual threads and JSON handling
- Model Context Protocol (MCP) integration
- Session persistence and management

It provides a comprehensive learning platform for understanding how AI agents interact with local environments and execute complex tasks through a modular, well-designed Java architecture.

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
