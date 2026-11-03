# EVA and internals

## Status and goal

Owner-authorized after the automatic-atmos/firelock sprint. Implementation is in progress; no complete EVA candidate has passed readiness or owner testing yet. The finite-liquid sprint remains deferred until shuttles are finished and the owner explicitly starts it.

Deliver a playable emergency-survival loop using existing finite gas, organ-backed respiration, environmental exposure, inventory, actions and wearable rendering: acquire/equip basic EVA clothing or a breathing mask, select/connect a finite tank, breathe from its regulated output, observe depletion/failure and survive the hazards appropriate to worn protection.

## Bounded scope

- Standard EVA suit/helmet, simple lowerable breathing mask, oxygen and emergency-oxygen tanks with empty/filled profiles.
- Exact body-owned tank selection; normal carried-slot movement, removal and mask lifecycle; existing action HUD/key system rather than a separate control pipeline.
- Tank-fed inhalation at resolved output pressure and existing respiration cadence. Exhalation and body pressure/temperature exposure remain ambient/containment-owned.
- Finite depletion, wrong-gas consequences and clear connected/low/empty feedback.
- Prototype-driven worn pressure/temperature protection, with incomplete pressure coverage unsafe and existing thermal regulation/body damage preserved.
- Recognizable worn assets, SS14 inventory/action sprites/audio and retained attribution; positionless real-gas manual fixture.

First loop uses sealed tanks with resolved outlet settings. Adjustable regulator UI, open-valve venting, TTVs/rupture, compressors, crafting, jetpacks and modular hardsuits are not implied. Do not introduce unsupported upstream components merely as inert JSON. Record limits honestly rather than claiming complete GasTank feature parity.

## Ordered work units

1. **Audit and contracts:** resolve pinned SS14 parent chains, defaults and consuming systems; map to current lung, equipment, atmosphere, inventory/action and rendering owners. See `PARITY.md`.
2. **Composition and exposure:** add supported typed clothing protection/breath-tool composition, inherited tank profiles/native factory mapping and live pressure/thermal consumption. Preserve existing unprotected behavior and damage owners.
3. **Owned source and breathing:** implement exact-token internals state, source lookup, validator-before-commit finite draws and inhale-only lung integration. Passive gas changes must use the existing immutable inventory commit pattern without cancelling unrelated timed actions.
4. **Controls and presentation:** extend item-provided actions and public appearance for tank selection, mask lowering and ingestion blocking; add feedback, actual wearable assets and native resource provenance.
5. **Playable fixture and grouped verification:** real registered-player action/equipment routes, global scheduled physiology, finite-room hazards and lifecycle cases; focused JVM/compile checks, applicable readiness and serialized integration/package gate. Owner manual acceptance remains separate.

Parallel units may edit independent owners; avoid running compilation against unfinished schema changes. Resolve all source API mismatches before grouped final checks. Delegate candidate preparation; the primary author alone registers/submits/acknowledges the complete owner-ready candidate.

## Acceptance criteria

- **EVA-01 — Resolved content and assets:** basic clothing and tank composition, defaults/inheritance and physical native identity are resolved and consumed live. New items are acquirable, correctly slotted, visibly worn and attributed; procedural/provisional copies are not finished artwork.
- **IN-01 — Connection lifecycle:** functional equipped mask or EVA helmet enables connection; actions identify the exact carried tank. Same-token slot moves preserve connection; removal/loss of the last breath tool disconnects. Known empty tanks remain connected without supplying free air. Multiple tanks remain distinguishable/addressable.
- **BR-01 — Finite respiration:** scheduled inhalation debits regulated finite gas, respects receiver capacity and preserves gas species; exhalation never refills the supply. Wrong gas reaches existing metabolism/toxicity. Unknown destinations are not synthetic vacuum; failed validation/commit loses no supply gas.
- **PR-01 — Pressure protection:** all resolved required slots must protect the body; weakest-part values govern felt pressure. Full basic EVA protects known vacuum through the upstream hazard-input floor; missing gear and ordinary radiation gear do not gain EVA immunity.
- **TH-01 — Thermal protection:** clothing attenuates external heat exchange before body-capacity conversion and finite environmental energy commit, not damage directly. Intrinsic regulation and direct reagent heat retain their source semantics; already dangerous body temperature still causes damage.
- **UX-01 — Usable controls:** reuse authenticated action discovery/admission/cooldowns and existing HUD/keys. Raised mask blocks ingestion; lowered state changes public appearance and breath-tool function. Feedback uses readable names and meaningful units; connect/disconnect/low/empty are distinguishable.
- **IS-01 — Inventory continuity:** draws update the canonical tank through the commit gateway and replicate contents without ownership revision churn. Unrelated timed actions survive only recognized passive debits; structural, token and relevant tool changes still invalidate them.
- **VAL-01 — End-to-end evidence:** tagged fixtures exercise real registered-player dispatch and global scheduled physiology, alongside prototype/consumer/unit coverage, package/provenance and concise manual steps. No fake peer/private atmosphere helper is claimed as authenticated/global integration proof.

## Known integration boundaries

Current atmosphere is active through `AtmosphereService.INSTANCE`; older handoff statements about a default-off enable flag are historical and do not describe current configuration. Do not rewrite owner atmosphere configuration for tests.

Tank breathing is a lung-only source override. Shared `ExposureAirRouting` continues to represent ambient/contained exposure for barotrauma, body temperature and exhalation. Tank internal pressure, regulated output pressure and surrounding atmospheric pressure are different quantities.

## Starting points

- `docs/active-capabilities-sprint/HANDOFF.md`, `docs/hands-inventory-program/HANDOFF.md`, `docs/equipment-sprint/HANDOFF.md`, `docs/blood-bleeding-sprint/lungs-and-puddles-extension.md`.
- `ms14/lung/`, `ms14/atmos/tank/`, `ms14/atmos/exposure/`, `ms14/equipment/`, `ms14/actions/`.
- `LiveHands`, `LiveHandsCommitGateway`, `WelderOwnerTickService` and `WelderFuelService` for passive owned-state precedent; `GasTankItem` for persistent finite tank stacks.
- `RegisteredServerPlayerFixture` and `RegisteredPlayerReadyDriver` for real native authority; existing finite-chamber initializers and `FixtureSiteContext` for testing setup.
