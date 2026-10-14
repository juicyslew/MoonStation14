---
description: Permanent, bounded integration/readiness coordinator for the four-worker team.
mode: all
color: "#FF8C42"
model: openai/gpt-6.1-sol
reasoningEffort: medium
permission:
  "*": deny
  read: allow
  glob: allow
  grep: allow
  list: allow
  bash:
    "*": deny
    "*workflow.py*--help*": allow
    "*workflow.py*status*": allow
    "*workflow.py*gate-preflight*": allow
    "*workflow.py*gate-validate*": allow
    "*workflow.py*gate-clear-blocker*": allow
    "*workflow.py*candidate-review*": allow
    "*workflow.py*gate-finish*": allow
    "*workflow.py*gate-scope-review*": allow
    "*workflow.py*manual-tests*": allow
    "*workflow.py*manual-notify*": allow
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
  question: allow
  todowrite: allow
  edit:
    "*": deny
    "docs/**": allow
    "reports/**": allow
    "work/**": allow
  task:
    "*": deny
    investigate: allow
    ss14-expert: allow
    review: allow
    fidelity-reviewer: allow
  external_directory:
    "*": deny
    "C:/Users/William/Documents/Minecraft Mod Dev Files/MoonStation14/**": allow
    "C:/Users/William/Documents/Minecraft Mod Dev Files/MoonStation14-shared/**": allow
    "C:/Users/William/Documents/Minecraft Mod Dev Files/Asset Building Workspace/**": allow
    "C:/Users/William/Documents/SS14-dev/space-station-14/**": allow
    "C:/Users/William/AppData/Local/Temp/opencode/**": allow
  doom_loop: deny
---

Read and follow the shared role contract at `C:/Users/William/Documents/Minecraft Mod Dev Files/MoonStation14-shared/docs/development-workflow/roles/integration-lead.md` before acting. The local profile header defines model, permissions and color; the shared contract defines role policy.
