# 2026-09-23 Milestone 8 final effect matrix

**Scope:** The current, bounded MoonStation14 behavior for every `EffectData`
variant, based on the registered handlers and the exhaustive support-matrix
test. “Supported” means supported within the precise local boundary below; it
does not imply full SS14 fidelity.

## Final matrix

| Effect | Classification | Current behavior and boundary / missing owner |
|---|---|---|
| `EvenHealthChange` | Supported | Applies aggregate healing or damage with deterministic canonical residue handling. Vanilla damage aggregation does not provide per-typed-key armor/resistance allocation; death/totem ledger reconciliation is limited. |
| `HealthChange` | Supported | Uses final Post damage, separate resistible/bypass vanilla sources, absorption, cooldown bypass, baseline import, and vanilla death handling. Typed allocation preserves requested proportions because per-key mitigation allocation is not exposed. |
| `Vomit` | Partial | Eligible living stomach targets empty their stomach solution, including the live digestion transaction, and attempt to spill actual contents. Initialized custom hunger/thirst owners receive -40; status-capable targets receive the bounded 0.5 movement slowdown for 267 ticks. No bloodstream purge, forensics, synthetic vomit reagent, or atomic spill egress; failed placement can lose emptied contents. Unsupported/dead targets remain untouched. |
| `Jitter` | Supported | Uses the authoritative status attachment, bounded jitter payload, and local-player camera presentation; remote-observer parity is not claimed. Eligible finite immediate metabolism UPDATEs receive a LOCAL 21-tick ongoing-source floor; this is not pinned-upstream duration semantics. |
| `Drunk` | Supported | Adds the authoritative drunk status for scaled booze-power duration. Full drunk speech/visual projection is outside this handler boundary. |
| `ModifyBleed` | Explicit unsupported | No authoritative blood/bleed-rate owner; the former damage substitution is disabled. |
| `Oxygenate` | Explicit unsupported | No authoritative respiration or organ-saturation owner. |
| `ModifyLungGas` | Explicit unsupported | No authoritative lung gas-mixture owner. |
| `AdjustAlert` | Supported | Mutates character-owned alert state with absolute tick deadlines, category replacement, and compact local-player HUD; required prototype metadata/localization and local delivery boundary apply. Death/clone policy remains the attachment default. |
| `SatiateHunger` | Supported, bounded | Applies to custom hunger `[0,200]` (default 150); eligible player/villager state initializes once in `[110,150)` and decays by SS14 threshold rate once per staggered 20 ticks. Passive owner decay, threshold alerts, and starving speed now exist. Server-side vanilla `FoodData.tick` is suppressed for temporarily eligible player bodies, preventing vanilla natural regeneration, food drain, and starvation from competing. The separate Peaceful `Player.aiStep` nutrition branch is also skipped for eligible server-side character players without changing the gamerule; client HUD and vanilla food-consumable mutation remain unresolved. This is not full hunger and has no configured starvation damage (upstream default is unconfigured); no jetpack exemption, elapsed-time catch-up, or full physiology. |
| `ModifyBloodLevel` | Explicit unsupported | No authoritative blood-volume/max/regulation owner; damage substitution is disabled. |
| `SatiateThirst` | Partial | Applies `factor * scale` only to already initialized thirst state on explicitly tagged eligible players/villagers, and updates thirst-owned alerts/speed projection. The effect does not initialize absent thirst; the thirst owner initializes eligible characters at join. No vanilla `FoodData`, character-prototype enrollment, jetpack exemption, or full thirst physiology. |
| `PopupMessage` | Supported | Uses registered message keys and the configured local/PVS delivery boundary. |
| `Emote` | Partial | Dispatches a closed lowercase allowlist to living entities through local sound/chat behavior. No species/vocal capability model; `force` does not bypass living-target admission and has no effect, and `showinguidebook` is metadata only. See [Emote audit](2026-09-23-emote.md). |
| `ModifyStatusEffect` | Supported | Uses typed status operations, runtime prototype resolution, and the status reducer/lifecycle. Individual status markers do not imply all upstream projections. Eligible finite immediate metabolism UPDATEs receive the LOCAL 21-tick ongoing-source floor. |
| `AdjustReagent` | Supported (body route) | Applies a capacity-safe routed source-solution transaction for explicitly routed body-source metabolism. It is explicitly skipped for stomach digestion because the handler lacks stomach-source capacity semantics; see [stomach ingestion audit](2026-09-23-stomach-ingestion.md). |
| `CureZombieInfection` | Explicit unsupported | Infection, immunity, and death-conversion owners are unavailable. |
| `ArtifactDurabilityRestore` | Explicit unsupported | Artifact node and durability owners are unavailable. |
| `ArtifactUnlock` | Explicit unsupported | Artifact node and unlock/progression owners are unavailable. |
| `GenericStatusEffect` | Partial | Compatibility adapter accepts only its explicit fixed allowlist; unknown aliases do not apply. This is not arbitrary component/reflection support. Eligible finite immediate metabolism UPDATEs receive the LOCAL 21-tick ongoing-source floor. |
| `Flammable` | Supported | For non-fire-immune living character targets, changes authoritative fire stacks and supports negative-stack wetness recovery. No fire ticks, burn simulation, visuals, spread, or equipment ignition; other targets are unsupported. |
| `Ignite` | Partial | For eligible non-fire-immune living character targets, sets authoritative ignition only when stacks are positive. Burning simulation, fire visuals/spread, and equipment ignition are absent; other targets are unsupported. |
| `AdjustTemperature` | Explicit unsupported | No authoritative temperature, heat-capacity, or energy-transfer owner. |
| `Extinguish` | Supported | For eligible non-fire-immune living character targets, clears authoritative ignition and applies stack/wetness semantics. Full burn simulation and other target capabilities are absent. |
| `MovementSpeedModifier` | Partial | Applies the local status/attribute behavior only for equal walk and sprint modifiers on eligible living status targets. Unequal pairs and unavailable target capability are unsupported. Eligible finite immediate metabolism UPDATEs receive the LOCAL 21-tick ongoing-source floor. |
| `CleanBloodstream` | Explicit unsupported | Route-aware bloodstream state and reagent exclusion rules are unavailable. |
| `MakeSentient` | Explicit unsupported | Mind/sentience and ghost-role control owners are unavailable. |
| `Polymorph` | Explicit unsupported | Entity replacement, control/inventory transfer, and revert owners are unavailable. |
| `ResetNarcolepsy` | Explicit unsupported | Authoritative narcolepsy incident/timer state is unavailable. |
| `ModifyKnockdown` | Partial | Mutates the independent authoritative `knockdown` status with local reducer semantics. Non-removal requests with crawling/drop flags are rejected; physical prone/crawling, action blocking, paralysis, and held-item dropping are not projected. |
| `Electrocute` | Partial | Living status-capable targets receive configured-coefficient, two-stage-truncated resistible typed shock damage and the authoritative stunned marker. `bypassInsulation=false` is unsupported without conductivity; physical stun projection and SS14 presentation are absent. |
| `EyeDamage` | Partial | Persists character-owned integer damage in `[0,9]`; threshold 9 derives blindness presentation as client fog. Graded partial injury presentation, eye anatomy, and full vision behavior are absent; nonliving targets are unsupported. |
| `ReduceRotting` | Explicit unsupported | Authoritative rot and death/decay state is unavailable. |
| `CauseZombieInfection` | Explicit unsupported | Infection, immunity, and death-conversion owners are unavailable. |

There are **34 unique variants**: 11 supported within their documented local
boundary, 9 partial/compatibility-limited, and 14 explicitly unsupported. These
are behavior categories, not dispatcher registration counts: the dispatcher
has 20 handler registrations and 14 explicit unsupported registrations. The
11 supported and 9 partial effects together comprise those 20 handlers.

## Cross-system boundary and validation

The working [effect and status dependency ledger](../deferred-effect-dependency-ledger.md)
tracks all effect/status inventories separately. See the companion [Milestone 7
deferred-effects audit](2026-09-23-milestone-7-deferred-effects.md), [stomach
ingestion audit](2026-09-23-stomach-ingestion.md), [thirst owner audit](2026-09-23-thirst-owner.md),
and [reactive touch audit](2026-09-23-reactive-touch.md) for boundaries. Stomach
ingestion is a bounded 50-unit route using temporary player-and-villager opt-in.
The prior direct-contact M6 gameplay claim is superseded: puddle contact consumption
and effects are intentionally disabled until a real `SlipEvent`/slip-mechanics
path exists. The 34-effect
dispatcher matrix remains 20 handler registrations and 14 explicit unsupported
registrations; data-only reactive definitions are not gameplay handlers. These boundaries do not become full physiology or
full gameplay fidelity by virtue of this final matrix. Because the project
Definition of Done requires supported reactive effects to be reachable through
gameplay exposure, the missing slip-triggered exposure path means that overall
acceptance requirement remains open; this matrix records bounded effect
classification, not unqualified Milestone 8 completion.

Post-fix coordinator validation record: `test --rerun-tasks --no-daemon` and
`build` completed successfully. The isolated
`build/gametest-run/logs/latest.log` from
`runGameTestServer -Pms14GameTestDir=build/gametest-run` records all
**83 required GameTests passed** after the FoodData and Peaceful `Player.aiStep`
fixes. The Peaceful regression test exercises `aiStep` on an eligible Player
test double; it is server-side method-path coverage, not evidence from a
connected client. This is follow-up validation after reported
runtime issues prompted corrections; the original M8 completion was bounded,
and should not be read as evidence those issues were already resolved.
Resource semantic checks covered 411 raw / 406 resolved reagents, 19 status
definitions, and 5 alerts (the prior 3 plus peckish and starving); Python
parsed all 602 resource JSON files. Exact-zero puddle contents now remove the
puddle block. The runtime server also loaded recipes and
loot without the previous jug-loot parse error. The GameTest log includes the
expected test-generated missing-status error. Static JUnit coverage includes the
exhaustive 34-variant handler matrix; the static JUnit scan and successful
dedicated GameTest server jointly provide server-side classloading evidence,
not a GameTest exclusively checking classloading. These checks establish this documented bounded milestone,
not complete behavior for the 14 unsupported variants or project-wide fidelity.

The [manual test checklist](../manual-test-checklist.md) is a follow-up for a
human in a normal 1.21.1 client world. Its commands were checked for complete
parsing by `ManualCommandGameTests`; that parser-only test does not execute
them. No manual client test is claimed here.
