# Ships — Sable feasibility, physical flight and FTL delegation

## STATUS

**Revised 2026-10-10; planning/research only. No dependency integration, ship gameplay, benchmarks or runtime tests delivered.** The owner authorizes bounded Sable integration/feasibility work to proceed now through M0–M3; this is not sprint closure or technical adoption. Read [Instructions.md](Instructions.md) and [licensing-review.md](licensing-review.md).

**The previous discrete region-copy plan is superseded.** It is retained in [discrete-relocation-reference.md](discrete-relocation-reference.md), not a fallback automatically authorized by a failed feasibility test. Copy this whole directory into the worktree if uncommitted; a worktree created from HEAD will not include it. Preserve unrelated edits; no commit/push requested.

## ARCHITECTURE

New intention: evaluate **Sable persistent sublevels** first, then implement bounded real continuous flight around the moon, physical landing/docking and a separately gated FTL path primarily for dimension travel. Deep-space/CentComm gameplay, salvage incentives and evacuation round-ending remain deferred; an actual test destination dimension is required to prove transport, not that content.

Separate world-space body/physics/camera/reach from stable plot-space blocks/machinery/gas/cables. Extend existing query/storage/lifecycle owners, not duplicate solvers or remap every cell each frame. Preserve the real damaged hull and item/character ownership. The spectator carrier is not a passenger; a moving cabin is not disposal containment.

Sable remains a candidate, not technically adopted. Proceed with bounded Sable-only feasibility; optional Create/Aeronautics requires demonstrated utility and separate asset/dependency review. No generic moving-world engine, recurring reassembly, remote shell or two competing backends.

Normal moon travel is physical flight, not default same-map FTL. Large ships require compatible berths; living/loose objects are not structural arrival vetoes. Ordinary collisions and FTL arrival hazards are distinct. FTL warnings must be readable in 3D, including airborne players. The owner wants beyond-overlap FTL gib danger; approximately 3 m was discussed, not SS14's default. Resolve hazard/cover/escape-time policy together before claiming acceptance.

Stealth uses terrain/recessed appropriately sized berths. Flying antagonist ships may be observable; no distance cloak. Caves remain lunar terrain. A destroyed connector must not erase destination knowledge; upstream-like docking/proximity fallback is not exact-coordinate forced landing.

## IMPORTANT FILES

- `Instructions.md`: M0 license/artifact/parity baseline; M1 body/query/cargo proof; M2 existing simulation owners; M3 decisive seamlessness/performance go/no-go; M4 piloting/physical flight/docking; M5 FTL/actual dimension gate; M6 final limits/regressions.
- `licensing-review.md`: primary licenses, unresolved Shield noncompete question, distribution/asset obligations and suggested permission request. Owner accepts that interpretation uncertainty as a project risk for bounded M0–M3 prototypes; it is not legal clearance or permission.
- Newer local indexes: `docs/disposals-pneumatic-sprint/HANDOFF.md`, `docs/airlocks-sprint/HANDOFF.md`, loose-world-foundation/hands-inventory/timed-ingestion handoffs, pressure/power/APC/powered-ventilation successors, `docs/gametest-tags.md`. Historical lifecycle/experimental movement descriptions do not override current source.

Java paths below are under `src/main/java/com/juicyslew/moonstation14/`:

- `ms14/player_body_control/character/GroundedHarnessWorldStep`, `mixin/LivingEntityMindHarnessTravelMixin`, current control/snapshot/camera owner.
- `ms14/hands/live/BodyItemTargetProbe`, especially its `LoadedGetter`; body-hand authority/request/access services.
- `entities/LooseItemEntity`: native move, settled early returns and direct-world `hasFloorSupport`; `LooseClientPositionInterpolation`; shared pulling/timed-operation owners.
- `ms14/atmos/world/`, `atmos/exposure/{ExposureAirRouting,BarotraumaSystem,BodyTemperatureSystem}`, breathing and gas visuals.
- `ms14/power/`, `ms14/atmos/piping/`, pipe gas SavedData/lane UUIDs/device links, `ms14/containment/`, disposal holder state, `ms14/doors/`, prototype/runtime and shared UI.

## SS14 CORRESPONDENCE

Reverify external SS14 checkout `C:\Users\William\Documents\SS14-dev\space-station-14` at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. Persistent grids, piloting/thrusters, separate docking and grid FTL are upstream anchors; finish full supported parent/default/consumer mapping in M0.

Console chain: `ComputerShuttle → BaseComputerShuttle → BaseComputer → BaseStructureComputer → BaseStructure`. Port: `AirlockShuttle → AirlockRCDResistant → Airlock → BaseStructure`. Inspect pinned pilot/thruster and impact sources before designing new fields/controls. Local 3D physics/frame adaptations are not automatic full parity.

Ordinary FTL **server-config** defaults: 5.5 s startup, 20 s travel, 5 s arrival, 60 s cooldown. Arrivals-only 10 s cooldown, physics mass limit 300 and 5 s knockdown need their exact consumers; do not convert them to block-count/prototype guesses. Console coordinates/beacons can be FTL targets; `NoFTL` uses grid membership.

FTL `Smimsh` uses 0.2-expanded hard fixtures, gibs external bodies and queues other overlaps for deletion with exceptions. Configured giblets can scatter; universal flung salvage is not established. Normal moving collisions are separate. Volumetric warnings/wider shock and authored destinations are deliberate local adaptations. SS14 start/end sounds have verified /tg/station CC-BY-SA-3.0 attribution; no assets imported.

## DECISIONS / WHY

Sable NeoForge **2.0.6** release `fg9dTRz9` (2026-10-03) supports 1.21.1. Inspected **Sable** source `d501fb3103c3247e20cb2151a99182ad5dd61adb` declares NeoForge 21.1.228; project uses 21.1.224. Build target is not necessarily minimum runtime requirement, and exact binary/source correspondence is unverified. Do not confuse this pin with SS14's.

Source supports persistent plot/pose motion, general `Entity.move` collision hooks and per-sublevel pose/velocity packets, not per-block movement copying. Default two synchronous-call physics substeps/game tick; some queries scan loaded sublevels. These are favorable architecture plus scaling risks, not a benchmark or cheap-parked-ships guarantee.

## KNOWN GAPS

Concrete integration work: our owned bodies cancel vanilla travel while Sable supplies living carried motion there; our non-Level loaded ray getter bypasses Sable projection; exposure samples world eyes rather than plot gas; settled item support/carry/contact logic needs ship awareness. Plot attachments/indexing, splits, client visuals, containment and actual cross-dimension transfer remain unproven. Same-pipeline teleport is not dimension transfer.

Shield permits noncompeting purposes and restricts competing products, including free ones and licensor/affiliate products. MoonStation's use is **not established prohibited or permitted**. The owner authorizes bounded M0–M3 feasibility despite this unresolved interpretation; no written permission has been received and no legal clearance is claimed. Companion MIT does not relicense Sable; restricted optional-mod assets and embedded libraries need separate review.

No combined efficiency or 20–100-client evidence. Existing full-suite failures remain; establish a current baseline and do not call new failures unrelated without evidence.

## VALIDATION

Source/license/documentation research only. No code, dependency installation, assets, game tests or benchmarks changed/run. Execution requires actual controlled-body connected proof, owner/state conservation, representative cargo while flying, parked/moving/split and watcher measurements, actual dimension round trip, focused plus broad-suite reporting and independent reuse/parity review. One computer suffices for feasibility, not authentic capacity certification.

## NEXT WORK

M0 records the unresolved license risk alongside exact artifact/dependency/native/notice audit, parity and current-owner baseline; M1–M3 feasibility may proceed under the dated owner decision below. Complete exact audits before any distribution, and keep assets within stated boundaries. M3 remains a technical go/no-go for Sable gameplay, not automatic authorization for broader public release. Seek project-specific clarification when public release is considered; no automatic approved redistribution follows. Pass gates → M4 flight/docking → M5 phased FTL and actual dimensions → M6 limits/regressions. Fail/block → concrete findings and owner decision, not silent copying-backend fallback. Update this handoff on actual owner closure, not because an empty vanilla ship flew.

## OWNER DECISION — 2026-10-10 (separate from assessment)

The owner chooses to move forward using Sable for bounded integration/feasibility, including M0–M3 prototypes, accepting the unresolved Shield interpretation as a project risk for this work. No written permission has been received; this is not legal clearance, a finding that the use is permitted, or authorization for automatic redistribution. No one is to contact the author on the owner's behalf. Author contact is deferred and may be considered later if integration gets far; clarification is recommended when public release is considered. Technical M3 go/no-go, exact artifact/dependency/native/notice audit, and asset boundaries remain in force, with applicable distribution review completed before distribution. This decision neither closes the sprint nor changes its historical discrete-relocation alternative.
