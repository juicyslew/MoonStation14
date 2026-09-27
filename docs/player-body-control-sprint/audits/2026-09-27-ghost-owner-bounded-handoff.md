# 2026-09-27 ghost owner bounded handoff

**Disposition: owner-directed bounded ghost feel/presentation smoke closed; minor turning-while-moving jitter deferred.** The owner reports the ghost is mostly working and movement feels smooth, with minor jitter remaining while turning and moving, and considers the ghost milestone done. This closes that bounded milestone by owner direction; it is not full SS14 acceptance, harness transfer, or complete connected mode/teleport/latency acceptance. The latest report did not include raw frame-level logs/video or explicit authenticated Begin/Commit evidence. Earlier `run/logs/latest.log` records at 2026-09-27 01:17/01:18 show commits and accepted motion from separate earlier sessions only, not this latest playtest. See the [owner smoke record](experimental-ghost-owner-smoke.md), [sprint instructions](../../../instructions-player-body-control-sprint.md), and [single Mind / harness route](../architecture/single-mind-harness-movement-route.md).

## Bounded result and deferred ledger

- **Owner observation:** ghost movement smooth/mostly working; minor visual/control jitter remains while turning and moving. Owner considers this ghost feel/presentation milestone done.
- **Evidence boundary:** no latest-session frame-level measurements, video, or authenticated Begin/Commit correlation supplied. Do not promote earlier session log lines into evidence for the latest report or claim runtime details not reported.
- **Deferred item — ghost presentation:** minor turning-while-moving jitter remains visible and unresolved. Keep it in the deferred ledger; no fix or root cause is asserted here. It is not a blocker to the owner's bounded milestone closeout, but remains follow-up rather than a silently accepted/fixed issue.
- **Vanilla spectator click:** owner likes clicking a mob to view its camera target. This is a vanilla camera preference only. It does not control the mob, bind/transfer the Mind, or implement possession.
- **Not accepted:** complete connected mode coverage, teleport/respawn/dimension transitions, latency acceptance, ghost↔body transfer, full SS14 parity, and general system acceptance.

The existing experiment remains operator-triggered and default-off. `experimentalVerticalSliceMovement` remains default-off and transitional; do not remove it or create a parallel human movement/prediction implementation. The HUMAN attachment on the spectator carrier remains unresolved, and no NPC transfer or on-join ghosting is implied.

## Next gated milestone: human harness on the shared Mind route

The next milestone is an explicitly gated human-harness adapter on the **same existing Mind protocol, prediction, and movement route** used by the ghost—not a third controller and not a parallel ServerPlayer human path.

1. **Pure contract first:** validate a human-harness adapter against the existing shared motor/route and Mind ownership invariants without changing runtime behavior.
2. **Authorized runtime handoff second:** only after separate review/authorization, test with a real connected owner. On successful possession the ghost must disappear, the prior body must remain in the world, and the same Mind must persist bound to the possessed harness. A failed handoff must preserve the original active ghost and ownership; never leave a half-transfer.
3. **Keep proof bounded:** owner-connected evidence is required for the runtime handoff. Camera movement or vanilla spectator click-to-view does not prove control or transfer. Broader mode, teleport, and lifecycle proof remains separately gated.

## Validation context (historical; no new validation run)

The latest reported automated full JUnit rerun and build passed on 2026-09-26 22:02. The 2026-09-27 01:40 dedicated GameTest run completed 124 tests but had 3 unrelated concurrent atmosphere failures: `sealedroomdiffusesgasandconservesspecies`, `skyexposedcellusesambientandrejectsinjection`, and `coveredexteriorsealingandbreachrespectownership`. Do not report the latest full GameTest suite as passing. No Java tests/build or new owner validation were run for this documentation handoff.
