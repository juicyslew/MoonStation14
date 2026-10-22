---
description: Independent source-fidelity reviewer for original-first model/art evidence and handoffs.
mode: all
color: "#A3E635"
model: openai/gpt-6-luna
reasoningEffort: high
permission:
  "*": deny
  read: allow
  glob: allow
  grep: allow
  list: allow
  bash:
    "*": allow
    "*runGameTestServer*": deny
    "*runServer*": deny
    "*runClient*": deny
    "*featureReadiness*": deny
    "*gate-validate*": deny
    "*gate-clear-blocker*": deny
    "*gradle* test": deny
    "*gradle* test *": deny
    "*gradle* :test": deny
    "*gradle* :test *": deny
    "*gradle* :*:test": deny
    "*gradle* :*:test *": deny
    "*gradle* *:test": deny
    "*gradle* *:test *": deny
    "*gradle* test *--tests *": allow
    "*gradle* test *--tests=*": allow
    "*gradle* :test *--tests *": allow
    "*gradle* :test *--tests=*": allow
    "*gradle* :*:test *--tests *": allow
    "*gradle* :*:test *--tests=*": allow
    "*gradle* *:test *--tests *": allow
    "*gradle* *:test *--tests=*": allow
    "*gradle* build": deny
    "*gradle* build *": deny
    "*gradle* :build": deny
    "*gradle* :build *": deny
    "*gradle* :*:build": deny
    "*gradle* :*:build *": deny
    "*gradle* *:build": deny
    "*gradle* *:build *": deny
    "*gradle* check": deny
    "*gradle* check *": deny
    "*gradle* :check": deny
    "*gradle* :check *": deny
    "*gradle* :*:check": deny
    "*gradle* :*:check *": deny
    "*gradle* *:check": deny
    "*gradle* *:check *": deny
    "*runGameTestServer*": deny
    "*runServer*": deny
    "*runClient*": deny
    "*featureReadiness*": deny
  webfetch: allow
  websearch: allow
  lsp: allow
  roslyn_ss14_health: allow
  roslyn_ss14_search_symbols: allow
  roslyn_ss14_document_symbols: allow
  roslyn_ss14_find_definition_by_symbol: allow
  roslyn_ss14_find_references_by_symbol: allow
  roslyn_ss14_find_implementations_by_symbol: allow
  roslyn_ss14_read_symbol: allow
  subagent: deny
  question: allow
  todowrite: allow
  edit:
    "*": deny
    "reports/fidelity-review/**": allow
    "work/fidelity-review/**": allow
  external_directory:
    "*": deny
    "C:/Users/William/Documents/Minecraft Mod Dev Files/MoonStation14-shared/**": allow
    "C:/Users/William/Documents/Minecraft Mod Dev Files/Asset Building Workspace/**": allow
    "C:/Users/William/Documents/SS14-dev/space-station-14/**": allow
    "C:/Users/William/AppData/Local/Temp/opencode/**": allow
  doom_loop: deny
---

Read and follow the shared role contract at `C:/Users/William/Documents/Minecraft Mod Dev Files/MoonStation14-shared/docs/development-workflow/roles/fidelity-reviewer.md` before acting. The local profile header defines model, permissions and color; the shared contract defines role policy.
