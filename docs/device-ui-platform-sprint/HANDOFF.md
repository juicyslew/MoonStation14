# Device UI platform proposal

## STATUS

Reconstructed 2026-10-03. **Deferred proposal, not implementation authorization.** Its original UI-first priority was superseded by lifecycle planning. Existing visual-kit and later machine UIs are delivered separately, not proof this proposal's gates passed.

## ARCHITECTURE

Proposed sequence: exact platform/menu lifecycle and client-classloading audit; harmless server-owned device using authenticated menus, versioned snapshots and bounded requests; connected stale-state/lifecycle tests; extract only proven shared primitives.

## IMPORTANT FILES

`proposal.md`, `../next-systems-roadmap.md`, `../player-character-lifecycle-sprint/HANDOFF.md`, and `../machine-ui-visual-kit-sprint/HANDOFF.md`.

## SS14 CORRESPONDENCE

Proposal cites pinned ChemMaster entity/shared/server/client sources as design references. They are not NeoForge APIs or a locally implemented ChemMaster.

## DECISIONS / WHY

No full ChemMaster, chemical prediction, broad gameplay API, or assumption that ServerPlayer menu transport establishes body authority. Prefer existing toolkit/services over duplicate frameworks.

## KNOWN GAPS

No acceptance record for this proposal's exact platform/connected lifecycle gates. Later implementations require inspection before deciding which proposed work is still needed.

## VALIDATION

No implementation/test results claimed for this proposal; reconstruction performs no tests.

## NEXT WORK

Do not start from this document alone. If separately authorized, reconcile delivered UIs and closed lifecycle work with current owner priorities, then perform only the missing M0 API/classloading audit.
