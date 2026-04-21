#!/bin/bash
# jcc → Ollama (Qwen3-Coder) smoke test.
#
# Prereqs:
#   - ollama serve is running
#   - ollama pull qwen3-coder:30b-a3b-fp16 has completed
#   - jcc is built (./gradlew :cli:installDist)
set -euo pipefail

JCC=/Users/azastashkov/projects/github/jcc/cli/build/install/jcc/bin/jcc
MODEL="${MODEL:-qwen3-coder:30b-a3b-fp16}"

export OPENAI_BASE_URL="http://localhost:11434/v1"
export OPENAI_API_KEY="ollama"
unset ANTHROPIC_API_KEY

echo "=== 1) jcc --version ==="
"$JCC" --version

echo
echo "=== 2) prompt text, streaming plaintext ==="
"$JCC" prompt \
  --model "$MODEL" \
  --max-tokens 200 \
  --permission-mode read-only \
  "Explain in two sentences what a Java record is. End with the exact word DONE."

echo
echo
echo "=== 3) prompt, NDJSON output ==="
"$JCC" prompt \
  --model "$MODEL" \
  --max-tokens 120 \
  --output-format json \
  --permission-mode read-only \
  "Count from 1 to 3. End with the exact word DONE."
