# Gas and disposal piping design

## STATUS

Reconstructed 2026-10-03. Original documents are conceptual/planning records, not implementation authorization. **Gas piping and powered room devices were subsequently delivered in `../powered-ventilation-sprint/HANDOFF.md`.** Do not repeat the historical claim that no local gas piping exists. Disposal remains future scope.

## ARCHITECTURE

Original design separates exposed 3D gas pipes from larger disposal transport. A clicked reachable eligible sealing wall hosts a straight pass-through without opening the wall or implicitly connecting room air. Room transfer requires a dedicated device/atmosphere endpoint. Current gas delivery implements three independent lanes and finite gas ownership; use its handoff for actual behavior.

## IMPORTANT FILES

`Instructions.md`, `architecture.md`, `milestones.md`, `references-and-open-decisions.md`; current implementation index: `../powered-ventilation-sprint/HANDOFF.md`.

## SS14 CORRESPONDENCE

Original proposal lists upstream targets but explicitly leaves composition/API audit incomplete. Later gas/device mapping verifies the pinned source and records Minecraft wallmount adaptations. No disposal-parity claim follows from gas implementation.

## DECISIONS / WHY

Do not share assumed gas/disposal capacities or transport semantics. Host stays sealed/full-collision; thick-wall traversal is not a remote action. Separate gas networks from room atmosphere and require proven finite transfer endpoints.

## KNOWN GAPS

Proposal removal, host, persistence and synchronization questions are historical where later gas work resolves them. Disposal transport/capacity/removal, scanner, protection, and representative scale still require their own decisions/evidence.

## VALIDATION

Original proposal has no implementation validation. Later gas tests/owner closure belong to the powered-ventilation handoff, not this plan. No tests rerun.

## NEXT WORK

Start from delivered gas architecture before extending it. Disposal needs explicit scope/authorization and its own upstream composition/platform audit; do not reopen resolved gas questions from stale proposal text.
