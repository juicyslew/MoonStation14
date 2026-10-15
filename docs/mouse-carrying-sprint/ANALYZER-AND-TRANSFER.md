# Shared character analyzer and hand-transfer escape

Implementation awaiting fresh integration validation; r40 remains the last
technically ready candidate. The owner again confirmed smooth held-camera motion.
No camera changes are part of this correction.

## Hand transfer

At pinned SS14 revision `c9df5ef5d675b0d1d226828bddf6b78c28502d91`,
`SharedHandsSystem.Drop.cs` uses `DoDrop(..., false)` for direct container transfer.
This suppresses the dropped interaction which cancels escape. Moving a mouse
between one holder's hands therefore must preserve its five-second escape.

`PortableBodyEscapeCustody` now uses a named mutable custody object retained by
the original timed-operation closures. Its active-operation handle belongs to
the existing `PortableBodyCarrierOwner`, not a new global registry. Repeated
resist requests do not replace it.

`BodyHandTransfer` resolves the exact source key's referenced body, validates
current custody before committing, and applies a one-shot refresh only after the
existing hand commit's success postconditions. The next owner must be registered
for the same body, carrier and canonical reference; the same operation must still
be active. A fresh strict release snapshot replaces the old locator without
changing operation ID/start tick. Post-commit validation deliberately does not
require the old, now-stale hand locator to remain current. Ordinary custody and
atomic release guards remain unchanged. Drop, throw, pickup and failed transfer
do not invoke this refresh.

## Analyzer

`HealthAnalyzerReadings` reads state from resolved medical policy and MobState
attachment independently of character identity. It does not infer state from
vanilla health. Unknown remains appropriate when medical state is unavailable.

`MedicalPortraitSnapshot` extends its existing detached-copy path to native
living entity types rather than a short entity allowlist. Existing specialized
appearance copying remains, including synced mouse appearance and cache
invalidation when that appearance changes. Portrait normalization changes only
the detached copy, not the live patient. Exact client entity identity validation
and unavailable/unsupported fallback remain in the existing renderer.

Upstream `HealthAnalyzerControl.xaml.cs` displays non-humanoid species as Unknown:
species, MobState and entity preview are independent. Owner-requested meaningful
animal species are a deliberate local adaptation. Resolved typed `Species`
composition supplies Human, Mouse and Pig labels; HumanoidProfile fallback is
retained. No entity-class/name inference or fabricated humanoid profile is used.
See `ANALYZER-SPECIES-ADAPTATION.md`.

## Cursor drag

The cursor icon is drawn by `StoragePopupScreen`, separately from workspace grid
previews. Its local `equipmentDrag`/`handGridDrag` could persist after escape
removed the source key; looking up the now-absent hand presentation fell back to
the native proxy item. `HandEquipmentGesture` now validates exact captured source
tokens against settled authority. The screen reconciles before painting, ticking
and click/drop admission. Removed or replaced sources clear the local drag; valid
sources remain. Temporarily absent authority proof does not invent source loss.
Existing workspace reconciliation and `ItemUiRenderer` remain the shared owners;
no mouse-specific placeholder texture or UI rendering branch was introduced.

## Validation boundaries

Focused source checks and independent reviews are not native runtime or GPU
evidence. The fresh gate must exercise transfer continuity, denied transfer,
drop/re-pickup cancellation, typed mouse medical states, resolved species and
detached nondefault portrait appearance, along with affected readiness and client
startup. Arbitrary native mob-specific appearance variants beyond the shared
copy adapters are not universally certified. Continuous-jump observer landing
presentation remains outside this correction.

After a validated build: move an escaping AI mouse between hands repeatedly;
drag a mouse icon under the cursor until it escapes; scan healthy, critical and
dead mice and verify species/model/state; compare another supported character;
recheck smooth held-camera motion.
