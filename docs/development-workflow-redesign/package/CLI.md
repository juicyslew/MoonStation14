# Local command reference (source-verified)

These are source-level CLI shapes, not an instruction to start a service. `python workflow_coordinator.py serve --config <external-config> --port <loopback-port>` is the coordinator command. `python workflow_supervisor.py serve --config <external-config> --port <loopback-port> --health-file <external-health-file>` is the supervisor command. Validate ports 1–65535; coordinator's internal readiness arguments are supervisor-only.

Submission helper source exposes `check` and `submit`, each with `--request`, `--context`, `--caller`, `--key`; `submit` additionally requires `--database` and `--artifact-dir`. Inputs are external private files. Do not inline full requests into an MCP call or shell handoff; use bounded local file references. No reset, generic queue-control or unsupported API command is documented here. For read-only status/snapshot consult the actual connected role tools and their schemas; status is saved metadata, not freshness or approval.

## Inactive WR10 package staging and verification

The publisher's actual stage-only interface is `publish_workflow.ps1 -CoordinatorPackage -PackageStage <absolute-stage> -PreviousReceipt <receipt> [-BindingPlan <private-absolute-json>]`. It rejects `-Apply` and other publication modes. Use only the exact MAIN-approved `build/agent-workflow/workflow-redesign-plan/wr10/stage` path or a new unique private child of `%LOCALAPPDATA%\Temp\opencode`. Staging is immutable/fail-stale: never clean, overwrite, or retry into a prior failed stage; choose a new private child. The publisher source script is an explicit MAIN source tool, not automatically a runtime package entry point. Staging enables no installed targets.

Example syntax only (do not execute as part of WR10 documentation work):

```powershell
$main = 'C:\<MAIN-root>'
$stage = Join-Path $main 'build\agent-workflow\workflow-redesign-plan\wr10\stage'
& (Join-Path $main 'tooling\agent_workflow\publish_workflow.ps1') `
  -CoordinatorPackage -PackageStage $stage `
  -PreviousReceipt 'C:\<absolute-current-publication-receipt>' `
  -BindingPlan 'C:\Users\<user>\AppData\Local\Temp\opencode\<private-plan>.json'
```

`-BindingPlan` is optional and runtime-only-incomplete without explicit target bindings; it does not make the package a complete cutover. Plan JSON requires `version: 1`, an explicit `coordinator_endpoint`, and `targets`; the listener endpoint is plain HTTP loopback and must differ from each target's `opencode_server` OpenCode API origin. Each target has `target`, absolute private `coordinator_config`, and explicit `roles`. Target labels are `main` or `MoonStation14-agent-01` through `MoonStation14-agent-05`. Static author roles bind existing `role`, `agent`, `caller`, `session_id`, `worktree`, and `token_file`; selected reviewer roles bind their dedicated `workflow-reviewer-<role>` agent, caller, worktree, and token file, with no static session ID. Reviewers receive fresh assigned sessions at runtime. Only author/selected reviewer roles need be listed; do not fabricate four reviewers for every target. Values must match the private coordinator configuration; reviewer source receipts are model provenance, not current runtime identity. Tokens and private configs stay outside worktrees/package; values are loaded into process memory only. Receipt-owned config/profile bytes and backups are hash verified. The staged plan never applies them. The disabled placeholder is [`binding-plan.example.json`](templates/binding-plan.example.json); extract only its inner `configuration` into a private plan after replacing placeholders.

Installer source is `install_workflow_coordinator.ps1 -PackageRoot <stage>`. Default invocation performs a frozen-byte/metadata plan only. `-VerifyOnly -PythonPath <explicit-pinned-python> -PackageRoot <stage>` checks the five dependency metadata pins without pip installation or environment mutation. `-Install` is deliberately refused in WR10. Example syntax only:

```powershell
$installer = Join-Path $stage 'tooling\agent_workflow\install_workflow_coordinator.ps1'
& $installer -PackageRoot $stage
& $installer -VerifyOnly -PythonPath 'C:\<explicit-pinned-python.exe>' -PackageRoot $stage
```

The separate future environment is `.venv-workflow-coordinator`; do not alter `.venv-workflow-mcp`. No activation, target installation, or config application is provided by these WR10 commands.

### Deferred administrative dependency bootstrap (WR12 only)

This is an unexecuted administrative prerequisite recipe, not an agent handoff, shell fallback, package installer feature, or runtime activation. Only after WR12's fresh baseline, intake hold, active-identity drain, and receipt-owned package-stage verification, confirm the explicitly approved shared path does not already contain `.venv-workflow-coordinator` unless its ownership is proven. Then create a new environment and install only the verified staged requirements:

```powershell
& 'C:\<explicit-base-python.exe>' -m venv 'C:\<approved-shared>\.venv-workflow-coordinator'
& 'C:\<approved-shared>\.venv-workflow-coordinator\Scripts\python.exe' -m pip install --disable-pip-version-check -r 'C:\<verified-stage>\tooling\agent_workflow\requirements-workflow-coordinator.txt'
& 'C:\<verified-stage>\tooling\agent_workflow\install_workflow_coordinator.ps1' -VerifyOnly -PythonPath 'C:\<approved-shared>\.venv-workflow-coordinator\Scripts\python.exe' -PackageRoot 'C:\<verified-stage>'
```

The final command checks all five pinned dependency versions before any later service start. This recipe has not been run and does not prove a clean environment install. Never alter/reuse the active `.venv-workflow-mcp`; do not select or change models. Recheck source-root, receipt and staged-byte freshness immediately before any later live operation. WR10 `-Install` remains deliberately refused; this documents only a bounded future dependency setup, not a general installer/apply engine.
