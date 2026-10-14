# Independent review report template

Use this as report content, not as a guessed CLI/JSON schema. Record the report through the current workflow helper only after checking [CLI.md](CLI.md) and its help. Keep it short enough for an owner decision; omit sections that do not apply but never imply unperformed checks.

```text
Candidate/request ID:
Worker / worktree / branch / submitted revision:
Reviewer (independent of implementation):
Decision: needs_changes | ready_for_owner

Scope and changed paths reviewed:
Build/test identity and exact checks examined:
Coverage that exercises the changed behavior:
Failures, known unrelated baseline issues, and unverified boundaries:

For artwork only — current package/revision and source pin:
Actual original/candidate image paths + hashes opened:
Representative views/states/motion inspected:
Source-relative invariants that match:
Concrete mismatches, inference/departures, and blocking questions:
Provenance/license/complete-manifest evidence checked:

Required next action / concise reproducible manual check (if applicable):
Owner decision still required:
```

Never mark acceptance, merge, release, import or installed-runtime behavior based on this recommendation. A review decision must be accompanied by evidence in the workflow's current supported format; this template does not define that format.
