# Repository-wide SS14 prototype and composition discipline

For every future feature design or implementation, not just ventilation:

## Required readiness contracts for relevant work

Readiness contracts and checks are mandatory only when work affects their scope; unrelated work does not require them. Read only the matching documents: `docs/feature-readiness-sprint/ui.md` for gameplay windows, layout painting, pointer/focus/drag, and previews; `interaction.md` for entry paths, admission, and session activation/retention; `prediction.md` for prediction, immediate feedback, reconciliation, and timed progress. Read `README.md` only for commands, selectors, report interpretation, or preview setup. Do not scan the sprint directory to determine relevance; consult acceptance history, handoff, or manual material only when the specific task needs it.

For relevant work, reuse existing shared owners matching the contract and run applicable `featureReadiness` selectors before reporting completion. Select affected categories, not all categories by default. If coverage does not exercise changed behavior, extend tests; passing unrelated tests is not proof. Report blocked checks and unverified boundaries honestly; checks do not establish visual review or owner acceptance. This supplements, and does not replace, the prototype/composition rules below.

- Before designing fields or writing feature code, inspect the pinned Space Station 14 entity prototype's full parent chain, inherited components and their defaults, and the systems that actually consume them. Reference checkout: `SS14-dev/space-station-14` under the developer's Documents (or an equivalent checkout), pinned to `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. Verify the checkout revision before relying on it.
- Map that resolved upstream composition to local typed prototypes: represent relevant inheritance and composed components, distinguish upstream behavior from Minecraft adaptations, and never introduce components the local runtime does not support just to mimic upstream YAML.
- Wire live gameplay to consume **resolved** local prototypes, including inherited defaults; a JSON definition alone or hardcoded-only behavior is not a prototype/composition implementation. Record verified parity and deliberate departures with source references and rationale. If upstream is unavailable, label the mapping **unverified** and do not claim parity.
- Test prototype resolution, inheritance/defaults, and the live runtime consumer path for the feature. Preserve existing project priorities, asset/provenance constraints, and prediction/authority boundaries while doing so.

Pure bugfixes that do not design or extend a feature need not repeat the upstream prototype audit; do not use that exception to bypass the audit for new behavior.

## Keep documentation lightweight

- Do not place models or large files in `docs/`, including model-source/export copies, asset bundles, archives, binaries, recordings, profiler captures, or bulk generated output. Documentation is not an asset backup or evidence dump.
- Keep game assets in their canonical runtime/resource or asset-source locations. In docs, reference those paths and relevant prototype IDs instead of duplicating the files.
- Do not archive uninstalled or unwired models in docs or present them as delivered. Integrate and verify them through the normal asset workflow, or explicitly report the missing integration.
- Keep raw test/capture output in ignored build output or the coordinator-managed evidence archive. Docs should contain concise summaries and references to that evidence, not copies of large artifacts.

## GameTest guidance

Add `@GameTestTags` to relevant fixture classes or methods and use `./gradlew runGameTestServer "-Pms14GameTestTags=<tag[,tag...]>"` for focused checks. For server validation, prefer a separate directory (`-Pms14GameTestDir=build/gametest-tags-fixture`) so owner run configuration cannot alter fixture assumptions; never overwrite owner configuration. Preserve failed artifacts when useful. Report filtered/full status and selected/total plus pass/failure counts; tags do not provide isolation. Run/report the full GameTest suite for broad changes. See `docs/gametest-tags.md` for current usage and selectors.

## Shared worker and review workflow

If you editing the code of the mod AND ONLY if that's the case, start at [the shared workflow guide](../MoonStation14-shared/docs/development-workflow/README.md) and matching scoped guides. Workers run focused cheap checks and return their candidate to the primary author; the primary author submits and automatically dispatches the named integration lead's serialized integration/readiness gate. A `needs_changes` result returns to the author for correction and resubmission; work does not advance as technically ready until that gate passes. A technically ready candidate receives a concise manual-testing log for the owner. Significant work/sprints require explicit owner acceptance; automated readiness is not acceptance. The integration lead alone owns expensive shared-machine validation (full builds/tests, server/client and readiness suites); authors may run focused unit/JVM checks and compile checks. This supplements, not overrides, prototype/readiness and asset-package instructions.
For normal validation, select affected JVM classes, only actually affected readiness categories, affected native GameTest tags and shared integration boundaries; do not automatically bundle ui, interaction and prediction. Group related work before expensive final submission rather than running a new heavy check after each minor step. Diagnostics state the question each check answers in its existing rationale and begin with the cheapest capable check; diagnostic intent alone does not trigger native/package/full-gate checks or debug logging. Preserve trusted selector and prototype/runtime coverage, OWNER_READY package/provenance requirements, and existing broad-change/MAIN full-suite requirements. Tags are not isolation or proof of coverage; inspect existing bounded failure evidence before rerunning.
If you are doing design work, or coding work that doesn't touch the mod's code (like work around improving our process for instance), then DON'T use the shared workflow guide.

Delegated finite cheap checks must complete in the worker's foreground command before the worker reports; never return a background-shell “running” state as terminal evidence. A parent completion notification does not subscribe to later background process output. Use the serialized integration owner for genuinely long work, report detached checks as unverified, and bind waits to known live producers rather than polling or assuming callbacks.

## Workflow error recurrence prevention

Treat reported workflow infrastructure errors as defects, not routine outcomes to retry indefinitely. Reproduce the failure under the affected role's actual permitted tools, audit the complete required input/output path for adjacent capability gaps, and add a public SDK regression covering that path. Filesystem or store-only checks do not prove a restricted agent can complete its workflow. Prefer fixing and deploying the pipeline, cancelling/retiring broken runs, and allowing fresh complete submissions. Do not build complicated recovery or information-retention machinery just to salvage old cards or avoid validation reruns. Keep ordinary immutable history where the existing workflow already does so; it must not become the bottleneck.
Use the canonical reviewer continuation/retry owner for repairs. Do not inject ad-hoc prompts into bound reviewer histories: even well-intended notices can invalidate the history checks needed for safe retry and cleanup.
