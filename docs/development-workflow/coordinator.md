# Integration coordinator

Entry point for the serialized mod-code readiness gate: use the [shared coding-gate contract](README.md) and the [integration-lead role](roles/integration-lead.md). Consult [CLI.md](CLI.md) or relevant command `--help` only when an operation/schema is needed. Root `AGENTS.md`, applicable readiness contracts, and candidate handoff remain authoritative.

This directory is neutral, not a Git worktree; where required, pass `--asset-workspace "../Asset Building Workspace"` to use the existing exchange. Candidate reports and logs belong under the CLI-returned author-worktree path. Technical verdicts are not owner acceptance. See [HANDOFF.md](HANDOFF.md) for current implementation status and known operational gaps.
