# STATUS

EVA/internals and the requested manual-testing corrections are implemented. Owner reported manual testing complete and instructed work to stop on 2026-11-03. The already-admitted final gate subsequently settled PASS: `run_5cf6e85974d215c9e17d2ca5`, generation 1; result acknowledged and cleanup confirmed. No further validation or resubmission is authorized by the owner's stop instruction. No merge authorization is implied.

Accepted replacement wearable family is imported. Carrier-isolation redesign and finite liquids remain deferred; neither belongs to this closure.

# ARCHITECTURE

- Resolved equipment prototypes compose pressure/temperature protection, breath tools, ingestion blocking, actions, delays, worn models and clothing sounds. Relevant defaults live in meaningful abstract parents, not special-case leaf-ID logic.
- `InternalsSystem` binds supply to an exact body-owned item token, allowing normal carried-slot changes but disconnecting when the source or last functional breath tool is lost. Only inhalation draws tank gas; exhalation, pressure and thermal exposure remain ambient/containment-owned.
- Tank contents remain immutable owned-stack components. Regulated draws validate the lung candidate before committing through `LiveHandsCommitGateway`. Passive debits retain ownership revision; bounded continuity proofs preserve unrelated timed operations. Final depletion may remove gas-species keys, including the last key.
- Required pressure slots must all protect the body; weakest-part protection applies to felt pressure. Thermal coefficients multiply over applicable worn non-pocket items and attenuate external exchange while conserving environmental energy. Intrinsic regulation and direct reagent heat remain unattenuated.
- Existing item-provided actions supply source-specific internals and mask controls. Slots are contiguous, without a debug reservation; keys 1–9 route to the controlled character before vanilla hotbar/spectator handling, retaining UI/F3/ghost boundaries.
- Examination has explicit direct-item and stored-item targets in addition to world targets. Canonical owner readers validate slot/token/revision and live world-storage sessions; common contributors receive fresh stack copies. Client refresh preserves logical item identity and invalidates replacement, movement or expired access. Inventory targets have no world geometry and must never enter block-outline lookup.
- Vocal and clothing sounds use the shared entity-attached router and body-view audience. Clothing audio is derived once after committed equipment changes, with batch-first/batch-final snapshots and exact occupant tokens. Failed or compensated changes do not create wear cues; component-only updates are silent.

# IMPORTANT FILES

Paths below are under `src/main/java/com/juicyslew/moonstation14/` unless stated otherwise.

- `ms14/lung/`: `InternalsSystem`, `LungSystem`, finite breathing and carrier eligibility.
- `ms14/atmos/tank/`: resolved tank profiles, contents and `GasTankDrawHelper`; `item/custom/GasTankItem.java` provides native factory identity and tooltips.
- `ms14/equipment/`: composition/merge/bootstrap, `EquipmentProtectionProjection`, `BreathMaskActions`, `BreathToolItemState`, `EquipmentWearAudio` and render planning.
- `ms14/hands/live/`: `LiveHands`, `LiveHandsCommitGateway`, canonical item/storage access and transfers; `ms14/hands/network/EquipmentTimedInteraction.java` owns normal delayed equipment requests.
- `ms14/actions/` and `ms14/actions/client/`: provider discovery, action admission, numbered slots and presentation.
- `ms14/examine/` and `ms14/examine/client/`: item identities, authoritative queries, contributors, pressure formatting, popup/latch and spatial classification.
- `ms14/audio/`: entity-attached/positional routing and shared body-view audience.
- `ms14/eva/fixture/`: one-command vacuum-ready setup. `MANUAL.md`, `PARITY.md`, `FOLLOWUPS.md`, `ASSETS.md` and `IMPORT.md` index contracts and source references.
- Native fixtures include `EvaInternalsGameTests`, `EvaFixtureCommandGameTests`, `GasTankItemExamineGameTests`, `EntityAttachedVocalGameTests`, `InternalsAudioGameTests` and `EquipmentWearAudioGameTests` under `src/gametest/java/`.

# SS14 CORRESPONDENCE

Reference pin: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

- Filled oxygen: 5 L, 2.051379050 mol O₂; emergency: 0.66 L, 0.270782035 mol O₂; both 293.15 K. Outlet defaults to 21.3 kPa. Human updates alternate every two seconds: one inhale per four seconds. Low warning compares internal tank pressure to 30 kPa.
- Basic helmet and suit pressure multipliers are high 0.6 / low 1000; they do not multiply together. Known hazard input is floored at 1 kPa before felt-pressure protection, including known physical vacuum. Unknown samples remain unknown.
- Helmet thermal coefficients are 0.2; suit coefficients 0.01. Helmet equip and removal each take 40 Minecraft ticks through normal authenticated requests.
- Helmet itself is a breath tool. Lowering a separate mask need not disconnect internals while that helmet remains worn. Raising a mask does not automatically reconnect the tank.
- Mask action uses the gas-mask icon when raised and blocked icon when lowered. An alternate toggle icon suppresses the extra pressed-background treatment. Internals action uses `internal2` off / `internal1` on; animated `internal0` belongs to the separate low-pressure alert, not the action.
- Tank examine reports rounded pressure in detail range plus connection and closed-valve state. Pressure rounds before SS14-style one-decimal scaled units. No invented remaining-time, temperature or composition examination fields.
- Helmet equip/removal use `mechmove03.ogg`; grey jumpsuit equip uses `jumpsuit_equip.ogg`, with silent removal. EVA suit, mask equipment operations and mask lowering/raising are silent. Internals connection is a +5 dB one-shot; default disconnect is silent, with no continuous flow loop.

# DECISIONS / WHY

The initial tank loop is sealed: regulator UI, venting, rupture/TTVs, compressors, jetpacks and modular hardsuits were not added. Existing gas, damage, inventory, action, examination and renderer owners were extended rather than duplicated.

`/ms14 eva_fixture build` now prepares worn EVA gear and connected finite oxygen, seeds a lit finite vacuum room, and enters the authorized body. Obsolete public `initialize`, `enter` and `kit` commands are removed. `reset` restores room air only, not health or tank contents.

The native player transport carrier is not a second physiological character. Lung policy/cache paths now exclude it through the existing carrier classification; broader carrier restrictions remain a separate paused architecture task.

# KNOWN GAPS

`jumpsuit_equip.ogg` matches pinned upstream bytes, but its attribution/license remains unresolved; see `docs/audio-unknown-provenance.md`. Do not claim redistribution clearance.

Art request `eva-wearable-replacement-20261102-r1`, revision `eva-source-faithful-worn-family-r1`, is accepted and imported: six meshes and six textures match delivery digest `0bb515a8f6e9e041566359d163b4f245110c8438b4f88e0fc9f689fe6d8cec7e`. Editable projects and previews remain in the Asset Building Workspace. CPU/source acceptance and import hashes are not independent proof of every GPU pose, species or lighting condition.

# VALIDATION

Final serialized gate covered 76 selected JVM classes, UI/interaction readiness, focused native tags, package/provenance and independent reviews. Native coverage includes real registered-player equipment/action/storage paths, finite respiration, protection, carrier exclusion, examination and post-commit audio. A fixture correction scoped helmet sound assertions to its emitting body; unrelated audible fixtures are not test isolation.

# NEXT WORK

Await only owner-directed follow-up or merge instructions. Do not resume carrier hardening, liquids, validation or resubmission automatically.
