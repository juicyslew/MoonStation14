# Guns work — consolidation pause

## Status

Paused at the owner's request for worktree consolidation and an SS14 ECS
architecture refactor. **This is not sprint completion or owner acceptance.**
No further feature changes, refactoring, validation runs, submissions, commits,
or merges are authorized by this note.

- Worktree: `MoonStation14-agent-02`, branch `agent/02`.
- HEAD at pause: `b606b6bddbdcce0ff32bb43fe726bdf45f890635`.
- Work remains uncommitted, with substantial **untracked** source, resources,
  tests and documentation. A tracked-only `git diff` will omit important work.
- Last code candidate: technical PASS `run_7b7a17b19c1928169233a994`, generation 1;
  result acknowledged, `DECIDED/PASS`, cleanup `CONFIRMED`. Nothing is running
  for this candidate and no resubmission is planned.
- That PASS establishes the selected checks, **not SS14 ECS architectural
  compliance**, native GPU appearance, owner acceptance, or readiness after merging.

## Work present

Eight selected guns: Mk58, Lecter, Inspector, Kammerer, Mosin, retro Laser Gun,
Energy Shotgun and Svalinn. The accumulated work includes item-owned ammunition,
magazines/cells, manipulation, wield reservations, persistent spent outputs,
powered capacitor recharging, hand counters/examination, and imported artist models.

The latest correction adds:

- Actual held-model muzzle-frame capture and a controlled-body first-person
  arms/items pass; presentation-only public hand/item/shot correlation.
- Closed beam tubes and pixel-volume effects, native alpha-cutoff handling,
  stable world orientation, and resource reload invalidation.
- Ground-gun controls through the existing retained target-action window and
  shared held/world staging policy; nullable empty-hand actor proofs.
- Source-bound shot-reply/hand-revision barriers and shared feedback-cue selection
  to prevent the Inspector's stale-ammunition extra gunshot cue.
- Shared muted cell-detail typography and Svalinn's uniform `.65` held/world scale.
- Supported, stomach-down prone gun aim, body aim-facing, articulated arms,
  physical-palm mounting, clothing articulation and shared raised physical eye.

## Architecture concerns to carry into the refactor

I should not have treated prototype inheritance, behavior checks and a technical
gate as sufficient evidence of ECS parity. The following are concrete audit
starting points, **not an approved refactor design**:

1. **Aggregate capability profiles.** `combat/GunProfile.java` collects Gun,
   multiple ammunition-provider families, Wieldable/bonus, cell slots and battery
   modes in one record. Its merge/catalog plumbing resolves data, but this is not
   equivalent to independently composed SS14 components and their system owners.
   Inspect `GunProfileMergeStrategy`, `GunItemFactory`, provider kernels and
   `PrototypeRuntime` together before preserving those boundaries.
2. **Feature-specific orchestration and dispatch.** `GunFireSystem`, `GunUseSystem`,
   `GunWorldUsePolicy/System` and `GunControlAvailability` branch on that aggregate.
   `hands/network/BodyHandActionRequest`, `BodyHandRequestService` and target-action
   snapshots contain gun-specific actions. Shared UI/planning reuse does not by
   itself make this SS14-like component/event architecture.
3. **Derived stance stored in posture and cross-system hooks.**
   `standing/PostureData.supportedGunAim` is persisted/synchronized derived state.
   `PostureSystem` consults gun/wield/combat admission; `CombatModeSystem` and
   `hands/live/LiveWieldSystem` call back into posture refresh. Audit this coupling,
   physical-eye ownership and state derivation rather than accepting the flag as
   the required architectural solution.
4. **Rendering ownership.** `GunArmPosePresentation`, `GunMountTransform`,
   `ProneGunTwoBonePose`, `ArticulatedHarnessModel` and
   `PlayerCharacterHarnessRenderer` bind gun behavior to the controlled-body rig.
   The supported-only lower-arm surface extension is a Minecraft animation
   adaptation, not upstream animation parity. Its mesh-only scaling must never
   enter the hand/weapon matrix. Ordinary native skin/UV behavior is regression
   tested, but these owners still need the architectural audit.
5. **Minecraft adapters versus simulation systems.** `TypedProjectileEntity`
   directly owns ticking/collision/damage integration, and runtime state uses
   ItemStack data components/attachments. Review where these are legitimate
   adapters and where simulation ownership should instead mirror SS14 systems.

## Important references and invariants

Java paths above are relative to `src/main/java/com/juicyslew/moonstation14/ms14/`
except `entities/TypedProjectileEntity.java`. Begin with `OWNER-FEEDBACK-ROUND4.md`,
`ROUND4-PARITY.md`, `presentation-bindings.md`, `ARTIST-MODELS.md` and
`ROOT-BINDINGS.md`; older sections of `CURRENT.md` describe superseded candidates.

- Upstream reference pin: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
- Moving shots use source speed 40 and lifetime 10s; this is not guaranteed
  collision-free range. Retro Laser/Svalinn use one 20-unit hitscan ray each.
  Energy Shotgun uses four/three moving projectiles in its two modes.
- Tiny native collider size previously culled projectile rendering near 6.4 blocks;
  presentation now uses the existing tracking envelope, without enlarging collision.
- Entity draw matrices are camera-relative world coordinates: muzzle capture adds
  camera position, not inverse camera rotation. Physics retains authoritative eye
  origins/hit points; visual anchors do not grant authority.
- Imported artist geometry, UVs, textures and display transforms must be preserved:
  260 baseline files, 250 exact, ten ROOT JSON differences limited to `overrides`,
  and 50 variant targets frozen-exact. Preserve attribution/noncommercial restrictions.
- Recheck ammo/cell/casing conservation, exact wield leases, source proof/epochs,
  predicted-feedback reconciliation and unrelated medical/turnover behavior after
  any architectural migration. Do not preserve legacy implementation merely to
  preserve a passing test.

## Validation and material to preserve

Last candidate: 101 selected JVM suites / 527 tests passed; UI/interaction/prediction
readiness passed; all 363 selected native GameTests passed with tags
`guns,hands,power,gun-fire`. This was **not the full GameTest suite**. CPU effect
and final native-material prone previews were independently reviewed; native
GPU/audio/client appearance and owner testing remain outstanding.

Ignored evidence is under `build/guns-sprint/workflow-owner-feedback-round4-r3/`
and `build/guns-sprint/round4-validation/native-ground-fixture-correction/final-01/`.
Preserve the evidence/provenance archives and the original `build/guns-sprint/artist-import/`
plans, receipts and frozen runtime baselines if later asset auditing is required;
Git alone does not carry those ignored directories. The last JAR is
`build/libs/moonstation14-0.0.1.jar`, SHA-256
`1c071fd73d28acf8b90c712b886484d5cc463d48f520b51be5c1472de0cfb162`.

Shared integration points likely to conflict across agents include `build.gradle`,
mod registration, attachments/data components, hand networking/client code,
prototype runtime/catalog wiring, `en_us.json`, sounds and the blocks atlas.
The dirty worktree also contains older cross-subsystem fixes; do not assume every
changed file belongs only to this correction. Untracked root `net/`, `data/` and
`OWNER-FEEDBACK-ROUND4.md` also need classification during consolidation; they have
not been deleted or treated as canonical mod sources in this pause.
