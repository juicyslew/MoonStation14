# Guns development state

## Paused for consolidation and architecture refactor

Owner-directed pause; **not sprint completion or acceptance**. See
[CONSOLIDATION-PAUSE.md](CONSOLIDATION-PAUSE.md) for uncommitted-work transfer notes,
the last technical validation, and explicit SS14 ECS architecture concerns.
No further feature work or resubmission is planned. Earlier status sections below
are historical, not an architectural-parity certification.

## Active correction: rendered muzzle sources and supported prone aim

Owner feedback after technical PASS `run_abafad5627dfcc17d1b4da16` is tracked
in [OWNER-FEEDBACK-ROUND4.md](OWNER-FEEDBACK-ROUND4.md). The source has changed;
that historical PASS does not validate this correction. Implementation and focused
validation are in progress; no round-four owner-readiness claim is made yet.

This correction replaces eye-offset flashes and flat effects with captured native
held-model muzzle frames and closed volumes, extends the existing ground controls
window, and introduces an articulated, raised, stomach-only supported gun stance.
Only validated wielded-gun harm aiming overrides the ordinary prone facing rule.
Shared cell-detail typography and Svalinn's uniform `.65` scale are also included.
The native `.1`-block projectile collider was incorrectly causing render culling
near 6.4 blocks; presentation visibility now follows the entity tracking envelope,
without changing SS14 projectile speed or lifetime.

The sections below describe earlier candidates and their historical decisions.

## Current correction: first-person effects, power and prone use

Technical PASS `run_401c3efcf2ac4289bf173e32` was followed by further owner
feedback, not acceptance. [OWNER-FEEDBACK-ROUND3.md](OWNER-FEEDBACK-ROUND3.md)
is the current scope; the earlier correction sections below are historical.

The candidate adds source-aligned hitscan caps/insets and first-person visual
clearance, world-travel-oriented projectiles, gun-only predicted cooldown retention,
standalone-cell charge HUD/examination, an APC-powered capacitor recharger, real
persistent spent outputs, and aimed arms for both prone rolls. Python's unsupported
runtime registration/model/name are removed, not merely hidden from Creative.
See [charging.md](charging.md) and [MK58-MANUAL.md](MK58-MANUAL.md).

The recharger reuses processing-power authorization and the shared machine-slot
ledger; it updates integral batteries or the actual installed cell. No selected gun
self-recharges. Prone presentation preserves existing posture/turnover authority;
supporting both rolls avoids forcing a new stomach-only gameplay rule. World-gun
context-menu redesign is deferred to the separate sprint requested by the owner.

Focused JVM checks and four cell HUD CPU previews have been reviewed during
development. The complete revised candidate still needs fresh final evidence and
the serialized gate. Native/GPU appearance and owner acceptance remain unverified.

## Whole-set correction (current)

`run_3b983fe361cb2e64f0c8ecf6` passed its technical gate, then received
whole-set owner feedback—not acceptance. [OWNER-FEEDBACK-ROUND2.md](OWNER-FEEDBACK-ROUND2.md)
supersedes the older acceptance work below.

- All eight guns use source-backed frame/anchor bindings and a shared native
  grip/display mount correction. Combat aiming follows head axes without turning
  the arm backward. Two-hand presentation uses bounded cross-body posing and
  reachable contact along the authored grip-to-support segment; this is a Minecraft
  one-bone adaptation, not an articulated SS14-style skeleton.
- Native casing atlas sources and previous/current roll interpolation are fixed.
  Spent cases remain cosmetic; live rounds remain recoverable items.
- All twelve imported loose profiles rest on their thinnest axis. Energy Shotgun's
  resolved model scale is `.7`, shared by held and world presentation.
- Shotgun fan-out is a deterministic 3D cone with unchanged counts and one debit.
  Energy muzzle resources, explicit sprite-sheet columns, beam UVs/width and
  projectile visual synchronization are corrected through existing effect owners.
- **Ctrl+Z** directly toggles wield; normal Z and medical modifier controls remain.
  Fixture-only Python is no longer exposed in Creative. See
  [MK58-MANUAL.md](MK58-MANUAL.md) for loading and finite energy supplies.

Pinned SS14 Mosin starts with ten logical rounds. None of the three selected energy
guns self-recharges; charger machinery remains excluded and absent locally.
The source candidate has focused JVM/resource/matrix evidence, including native
atlas-source enumeration, but still requires its fresh complete serialized gate.
GPU appearance and owner acceptance are not established. Imported artist geometry,
UVs, textures and display poses must remain unchanged.

## Current owner-testing correction

Technical PASS `run_4b7d9acb0beb24aab152cdbc` was followed by Mk58 owner
feedback, not acceptance. [OWNER-FEEDBACK.md](OWNER-FEEDBACK.md) records the
correction criteria; [MK58-MANUAL.md](MK58-MANUAL.md) is the focused retest.

The correction candidate fixes hand-button loading precedence and explicit
either-hand drag admission; materializes fresh standalone magazines once on
insertion; anchors readable gun/container counters to the source hand; uses the
humanoid head frame for forward aim; retains distinct rack presses through
correlated reconciliation; removes routine empty-fire text; and uses compact,
draggable/closable held controls with the established captured-cursor lifecycle.
Configured spent ejections are now textured, observer-routed cosmetic particles
with native voxel collision, damping/roll, finite lifetime and SS14 landing audio.
Live ejected rounds remain real recoverable items. Artist gun geometry is unchanged.

Fresh correction validation passed 57 classes / 322 tests, compilation and packaging;
independent review passed nine current CPU-window and four source-hand HUD previews, with byte-identical
final regeneration. See [OWNER-FEEDBACK-VALIDATION.md](OWNER-FEEDBACK-VALIDATION.md).
The first correction gate passed JVM/readiness and 270 / 272 native checks. The
two findings (rack event IDs and drop-fixture identity selection) are corrected;
the next run passed 271 / 272 and exposed a stale post-DROP pickup proof in the new
reload fixture. That proof is corrected. Live standalone-magazine and hand-row
HUD gaps found during final consumer review are also corrected and covered by the
new projection/layout tests and HUD previews. The complete replacement awaits a
fresh serialized gate. Native/GPU presentation
and owner acceptance are not claimed. The historical
gate notes below are not evidence for this revised candidate.

Historical planning notes below are superseded where they describe art/import work as
future work. The owner lifted the hold and instructed use of completed artist models.
Magazine R4, energy R3 and manual-feed R1 packages are imported; all 260 runtime files
matched their frozen hashes immediately after import. `ARTIST-MODELS.md` and
`ROOT-BINDINGS.md` are the authoritative current indices. This does not establish
owner/native/GPU acceptance.

## Implemented integration surfaces

- Ten native ROOT bindings (including Mosin), eight native guns, resolved gun/ammo/battery/shot profiles, shared provider
  kernels, copy initialization and server stage/commit/publish firing.
- Held-trigger start/continuation/stop, per-request correlation, selected-mode
  pacifism and confirmed wield evidence. Hold polling runs after hand reconciliation
  once per client tick; lookup/HUD reconciliation must not recursively poll firing.
- Held/world ammo insertion, detachable-container refills, direct chamber loading,
  held gun controls and source-bound shared menus. Slot ejection prefers free hands;
  cases/rack outputs go to the world. Bolt toggle has server and menu routes.
- Deferred wield cleanup through the existing level-post tick, preventing nested
  commit/drop item loss. Read-only hand UI distinguishes wield support from pulling.
- Moving patterns and hitscan, shared damage/armor/stamina routing, beam/impact
  presentation, sprite tint support, source gunshot/manipulation cues and spent-item
  model properties. Native loose-object profiles cover 12 profiles. Selective,
  resource-driven glow covers four regions. Mosin reagent-mask tint follows its
  solution appearance. Arm pose direction alignment is implemented. These describe
  implemented integration, not visual/native/GPU acceptance.
- Exact source storage footprints; Mosin's shared 2u refillable bayonet solution
  and 1u armor-piercing melee dose. Equipment native-item rendering and gun carry
  composition have been implemented in development units; verify their latest tests.

`IMPLEMENTATION.md` records architectural invariants and source-derived corrections.
`PARITY.md`, `PLAN.md`, `AUDIO.md` and `TEMPORARY-VISUALS.md` remain the relevant indexes.

## Next acceptance work

1. Submit the corrected complete replacement for readiness run
   `run_d9210ac9576fecd37865ce7c` (generation 1, FAIL, cleanup confirmed).
   Its readiness failure was a stale inventory-count assertion (218 versus 236
   bundled footprint profiles); the six abstract profiles remain excluded from
   230 resolved profiles. Revision 2 focused checks passed 40 classes / 247 tests.
   Subsequent native-fixture-only corrections use real magazine contents, a closed
   bolt after rack, and a rechambered round after auto-cycle. These received source
   review and fresh compilation; no native execution is claimed. Packaging passed.
   These author checks and corrections are not a readiness PASS.
   The replacement run `run_b52352994a035706c699b6f4` passed its focused JVM
   and selected readiness checks, but native startup failed before GameTests:
   loading `bullet_light_rifle` rejected canonical damage key `Piercing`.
   That run is settled FAIL with cleanup confirmed. The cause was authored
   resource casing, not initialization: four projectile definitions used source
   name `Piercing` instead of the local ledger's canonical `piercing` key.
   Their keys were corrected without changing values or weakening validation.
   A regression now resolves and decodes all bundled projectile profiles through
   the real prototype type/codec and stages every bundled production family
   through a fresh production manager, without touching global staged state.
   Revision 3 author checks passed 40 classes / 249 tests, all compile targets,
   and JAR packaging with the corrected projectile entries verified in the JAR.
   Run `run_a880b603d006271578293fbf` subsequently passed startup, focused JVM
   and selected readiness checks. Native selection ran 4 / 1,441 tests: Mk58
   and wielded Lecter drop passed; Energy Shotgun failed to fire and Svalinn's
   fixture constructed an invalid hand intent. The run is settled FAIL with
   cleanup confirmed. Both causes were fixture prerequisites: Energy Shotgun
   omitted the authenticated combat-mode enablement required by firing admission;
   Svalinn supplied an item token to token-free `SELECT_HAND`. Fixtures now use
   the existing legal requests, with protocol regression coverage and no weakening
   of production guards. Revision 4 author checks passed 41 classes / 253 tests,
   all compile targets and JAR packaging. Native execution must verify the
   corrected sequences.
   Run `run_5244f1457a1974571020e3d1` passed focused JVM/readiness and 3 of 4
   selected native tests, including Energy Shotgun's nonlethal impacts. Svalinn
   still failed because its later gun-reselection `SELECT_HAND` retained an
   item token. Both selection requests are now token-free; a JVM regression
   constructs the complete eject/select/drop/insert/reselect sequence and checks
   reselection codec roundtrip. Production hand-selection validation is unchanged.
   Revision 5 author checks passed 41 classes / 254 tests, compilation and packaging;
   the corrected full native sequence remains gate-owned validation.
2. Verify the internal `DRY_FIRE` path and casing conservation with focused tests:
   normal cadence/recoil memory, empty click rather than shot effects, and outputs
   published despite no projectile. Empty magazine *contents* and no magazine item
   are different. Manual rack always closes; only auto-fire with an empty present
   magazine opens the bolt. Keep source/target and pre/post-commit proofs distinct.
3. Finish or verify focused coverage for HUD glyph packing, cartridge model closure,
   refill and direct-chamber behavior, free-hand ejection, equipment carry rendering,
   and held menu/host contexts. Check actual live callers, not helpers alone.
4. Compile and later validate registered-content fixtures: Mk58, wielded Lecter
   drop, Energy Shotgun nonlethal impacts on a second registered body, and Svalinn
   cell swap. These must use real profiles, not fixture catalog replacements.
5. Manually review imported-model fit/animation, glow appearance and runtime GPU
   behavior; implementation exists, but source properties and successful import do
   not establish visual/native/GPU acceptance. Preserve artist geometry/UV/poses.
6. Only prepare a complete feature-readiness submission after the agreed scope is
   finished. Do not turn routine development checks into a diagnostic checkpoint.
   Heavy native/client/readiness execution belongs to the serialized integration
   owner, not workers. Ordinary local compile/focused JVM checks remain allowed.

## Known boundaries

- General wall/structure health is owner-deferred. Structural damage is not living
  health and must not be converted to Blunt or implemented as gun-only wall HP.
- Source energy reflection lacks a mapped reflective-target runtime. Energy fly-by
  audio is now mapped; remaining licensing and visual/native placement boundaries
  still require explicit review. Do not infer parity from data resolution alone.
- Some source audio licenses are unresolved and recorded in
  `docs/audio-unknown-provenance.md`.
- Temporary source ZIP archives were removed from `docs/guns-sprint/reference/`;
  the pinned manifests and curated findings remain. No unapproved model bytes
  should be imported during cleanup.
