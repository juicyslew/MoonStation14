# STATUS

Initial transport implemented 2026-10-12; automatic coding-gate extension and shared-runtime distribution added 2026-10-13. Shared publication and all five bindings are verified; live gate/notification verification remains pending. `MoonStation14-agent-05` / `agent/05` is the central integration/readiness workspace, created from `16bc66a`. Changes remain uncommitted; no gameplay/art assets or feature branches were merged. Start with [README.md](README.md).

# ARCHITECTURE

Main-repository `tooling/agent_workflow/` remains the development source. One runtime is published to sibling `MoonStation14-shared`, containing the CLI modules, workflow guides and role contracts. Worktrees retain only cwd-preserving CLI shims, workspace-specific permission/role headers and documentation pointers. They do not receive independent runtime copies. Git's common directory still resolves the shared operational queue at sibling `Asset Building Workspace/work/agent_exchange`; this is not a competing asset registry.

Model transport: request/context snapshot → exclusive claim → exact complete-manifest delivery → independent fidelity report → explicit owner acceptance → inspected destination plan → optional import. Automatic dispatch currently applies to Git coding candidates, not standalone asset deliveries.

Code flow: primary author submits an immutable candidate with a structured fixture/check plan → automatically calls `gate-request` → existing OpenCode service creates/reuses an integration-lead session rooted in 05 → independent inspection and serialized validation → `needs_changes` returned to the author, or a fresh manual-testing entry and Windows notification → explicit owner disposition. The author fixes rejected work and submits a new revision before advancing. Passing the technical gate never accepts a sprint or merges code.

`gate_dispatch.py` uses the authenticated `opencode api` CLI against the existing v2 service. Default requests omit `--server` so the CLI reuses its background-service credentials; bare URL requests to this installation require separate authentication. No credentials are read or logged by the workflow. No backend or watcher is started. Reviewer sessions are rooted in the candidate's worktree, avoiding broad peer access for helpers. An idle session without a persisted verdict is blocked, not approved.

`gate-validate` owns the shared-machine slot using an OS mutex keyed by the shared exchange. Typed evidence reuses current Gradle/JUnit/readiness/GameTest producers; exit zero alone is insufficient for those checks. Startup smoke checks stop their owned process tree after the declared marker. An unverified stop quarantines further validation until the integration lead explicitly confirms clearance. Logs, reports, artifacts and original/copied visual evidence remain hash-bound to the candidate.

# IMPORTANT FILES

- [CLI.md](CLI.md): supported schemas, commands and runnable examples.
- [coordinator.md](coordinator.md), `.opencode/agents/integration-lead.md`: fifth-agent role and authority.
- [source-fidelity.md](source-fidelity.md), [request-template.json](request-template.json), [review-report.md](review-report.md): early design checkpoint, original-first comparison and evidence requirements.
- `.opencode/agents/fidelity-reviewer.md`: independently invoked visual reviewer; also available in the asset workspace.
- `tooling/agent_workflow/{workflow,gate_dispatch,validation_evidence}.py`: shared lifecycle, existing-service dispatch, and producer evidence interpretation.
- `tooling/agent_workflow/test_*.py`: focused isolated lifecycle, process, evidence and mocked API fixtures.
- `tooling/agent_workflow/publish_workflow.ps1`, `workflow_entry.py`: dry-run-first shared publication and one-time local bindings; receipt-verified retirement of owned duplicate files, with backups. Future publications update only the shared payload.
- `docs/development-workflow/roles/`: shared role contracts; local agent files hold permissions/model settings and a pointer.
- Asset workspace `MODEL_HANDOFF.md`, `asset_locations.json`, package manifests and validators remain authoritative for retrieval and accepted/pending status.

# SS14 CORRESPONDENCE

No gameplay parity implementation was changed. Art review protects source-specific silhouettes, proportions, palette, anatomy, states and characteristic motion; source-authentic behavior, 3D adaptations and unsupported inference must be distinguished. Existing prototype/readiness obligations still apply to gameplay work.

# DECISIONS / WHY

Authors invoke the gate automatically at submission; the owner need not ask it to review the queue. The lead remains bounded, not an unattended daemon. Only it runs expensive server/client, GameTest, full build/JVM and readiness checks, through the shared runner. Authors retain cheap focused checks and correction ownership. Already-running sessions must adopt the updated instructions at their next task boundary; configuration reload is not proof of in-flight prompt refresh.

Workers stay within their assigned trees plus read/use access to the neutral shared runtime; author profiles may read 05 for handoff, while the 05 lead may inspect the four submitted worker trees. Scoped reviewers use their candidate tree. The consultant's publication access is not a worker grant. Role defaults remain coordinator `openai/gpt-6.1-sol`, medium, orange `#FF8C42`; fidelity reviewer `openai/gpt-6-luna`, high, lime `#A3E635`. Global models/default-agent settings are unchanged. Shell restrictions and actor labels are cooperative policy, not filesystem security sandboxes.

Reuse existing saved packages, manifests and validators; never regenerate to retrieve or select by timestamps. Import requires exact delivery/receipt hashes and a fresh destination plan. Predictable directory/parent conflicts fail before writes. Ordinary copy failures leave an explicit partial-failure record; rollback is not promised.

# KNOWN GAPS

Transport supports complete flat `files[{file,destination}]` manifests and byte copies only. Nested wearable manifests, unresolved geometry destinations and transformations such as MTL remapping are explicitly blocked. Hashes and PNG header checks establish evidence identity, not visual truth or owner identity. No Minecraft runtime or gameplay validation was needed or performed.

The earlier five-copy deployment has been retired. Publication receipt/backups are under approved temp `workflow-publish-78e222785a964fcb9552a63b77306cb2`; subsequent publications produce their own receipts. The one-time binding change invalidates older whole-worktree candidate snapshots: authors must resubmit them, not bypass freshness checks. The standalone modeling workspace is not a Git coding requester; its transport/fidelity/owner gates remain separate. OS delivery cannot prove the owner saw a notification. Minecraft execution is not established by mocked dispatcher or Python process tests.

# VALIDATION

Initial transport: 15 focused tests and independent code/documentation reviews passed; real 3,348-file compact-airlock planning was read-only. Nested wearable input was refused.

Gate extension: 45 focused Python tests passed (24 lifecycle/process, 10 producer evidence, 11 dispatcher). Independent core, dispatcher and corrected publisher reviews passed. Publisher fixtures include full fake-worktree dry-run, receipt ownership, scoped permissions and shim caller context. Actual shared publication/binding succeeded; repeated binding dry-run reported zero changes, and all five shim help commands passed. Read-only authenticated API checks resolved OpenCode 2.0.18 and the central role, correct model/color, shared contract pointer and four peer grants. The first live request (`workflow-setup-r1`) was blocked by direct-HTTP authentication before creating any session; it is not an approval. Authentication now reuses the CLI. A complete live callback and actual OS notification remain unverified.

Independent configuration review passed: OpenCode discovered the repository-local profiles and parsed their delegation, report-edit, external-directory, and read-only research-tool permissions. This checks loaded configuration, not malicious-command containment.

# NEXT WORK

Submit a fresh candidate through the published CLI for a bounded central-gate roundtrip and notification. Preserve per-tree prototype/readiness instructions and existing owner work. Use exact candidate/build/fixture identities for owner testing. Standalone asset automation and additional manifest shapes remain separate work; coding-gate readiness is not asset acceptance/import authorization.
