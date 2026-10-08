# Medical corrections

Owner-reported issues are corrective work, not accepted fixes until validation.

## Work units and acceptance

1. **Supply composition (1–2):** resolved maximum stack count 10 for gauze,
   brutepack and ointment, distinct from initial count; Small two-cell footprints
   for these supplies and the analyzer, Tiny one-cell syringe. Preserve detached
   count-one creative entries. Test native item caps, resolution and live storage.
2. **Treatment (12, 14):** gauze applies Slash/Piercing healing and the immediate
   bleed-amount delta even when no damage ledger entry changes; no Blunt healing.
   Begin/end sounds must reach controlled-camera listeners on admission/completion.
   Test bleed-only treatment, typed deltas and sound recipient routing.
3. **Analyzer (3–11, 13, 15–17):** smaller text and compact two-column layout,
   bounded dynamic height and scrolling, neutral upright frozen portrait, no
   patient-name line; valid linked target required to open, Z/same-target close,
   last-window close recaptures mouse. Storage and analyzer share sibling ordering
   for rendering, hit tests and focus. Unlocked-pointer body use remains available.
4. **Inventory publication (18):** one authoritative commit/publication seam,
   reusing owner snapshot networking. Immediate state updates must preserve query
   and command correlation, reject stale authority/revisions, and publish only
   final transfer states. Source guard prevents new direct attachment mutations.

## Established upstream facts

Reference revision: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
`BaseSmallStack` caps the three supply stack types at 10; full/single entity
prototypes independently start at 10/1. `BaseHealingItem` and `BaseItem` resolve
Small; basic syringe overrides Tiny. Small's weight is 2 and shape is
`0,0,0,1`; Tiny's weight is 1 and shape is `0,0,0,0`.
Gauze's bloodloss modifier is −10 to clamped BleedAmount, not a bleed-rate
multiplier. Analyzer damage groups sort by descending total, with types in
prototype order. Robust windows raise by moving to the last sibling.

The frozen portrait, omission of identity, compact arrangement and repeated-use
close controls are explicit owner-requested adaptations, not claimed upstream
behavior. Mouse recapture is a Minecraft-specific lifecycle requirement.

## Validation boundaries

Run unit/build and focused native integration checks after source integration;
run full native suite because the commit gateway crosses gameplay subsystems.
Record selected/total and pass/failure counts separately. Preserve known deferred
failures without assuming every failure is inherited. Connected visual sizing,
portrait appearance and actual keyboard/mouse interaction require client checks;
helper tests alone do not establish those outcomes.

Latest validation: build and 2,581 unit tests pass; medical native selection
passes 14/646. Broader hands/storage selection passes 181/183 and the full native
suite passes 635/646, with unresolved failures listed in `VALIDATION.md`.
Connected medical authority, visual fit and keyboard/mouse acceptance remain
unverified. This corrective work is not declared owner-accepted.
