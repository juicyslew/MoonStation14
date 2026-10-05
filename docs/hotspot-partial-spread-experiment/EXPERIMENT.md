# Partial-hotspot spread experiment

Authorized as a small, reversible follow-up after the pressure-transport sprint closed.

**Closed:** the owner accepted the faster live spread and requested closure. See `HANDOFF.md` for retained behavior, validation and remaining limitations.

## Change

An already-first-processed partial hotspot may expose face neighbors after a positive local combustion pass, using its local flame temperature above 423.15 K rather than requiring hot bulk gas and full-cell bypass status. Full-cell hotspots retain their bulk-temperature behavior.

Keep chemistry, ignition requirements, 95% partial/whole-air processing selection, 250 L neighbor exposure, 0.85 attenuation, receiver volume amplification, first-process skipping, deferred receiver processing, loaded/open topology and computational budgets unchanged. No water-vapor trigger, whole-cloud ignition or new heat-transfer model is added.

Production paths: `HotspotKernel.java`, `HotspotSpread.java`, and the hotspot commit/exposure paths in `AtmosphereService.java`. This intentionally departs from SS14's full-tile-only neighbor-spread restriction; it does not change tritium chemistry.

## Validation and acceptance

The owner live-tested the change, reported faster spread and accepted the result. The retained design departure is recorded in `../SS14-DESIGN-DEPARTURES.md`. Quantified before/after flame-front speed was not measured.

Focused kernel/planner and service commit tests passed. Review identified a remaining bulk-temperature guard in the service commit helper; it is corrected and independently verified. **14/14 selected GameTests** passed, covering reaction, fractional-chain and reactive-front fixtures. Unfiltered `compileGametestJava assemble` and diff check passed. Evidence: `build/hotspot-partial-spread-experiment/final-focused-junit/` and `final-gametest-latest.log`. Successful disposable worlds were removed after retaining logs. Faster live spread was confirmed qualitatively by the owner; no controlled speedup measurement is claimed.

Rebuilt `build/libs/moonstation14-0.0.1.jar`: 3,924,084 bytes, **2026-10-05 06:35:20 UTC**. SHA-256: `2BCF2D37F0A6FFDCE7F634BE03CE39B49EDCD193E09F06502D1D57B67F432231`. This accepted build supersedes the pressure-sprint artifact; it did not rerun the full 1,928-test/76-GameTest gate.

Earlier exposure may not be sufficient to ignite a cold receiver: exposure still creates metadata rather than directly heating its bulk gas, and receiver processing is unchanged. The experiment tests one gating change, not a replacement combustion model.

## Live comparison

Repeat the same tritium/oxygen grenade test. Compare time to the first neighboring flames and whether flame advances before the hot water-product wave. Note premature flicker/extinction or excessive spread. If there is no useful improvement, revert this experiment rather than changing chemistry.

## Reversal

Restore full-cell/bypass-only eligibility and bulk-temperature selection in the three production paths above, and restore the corresponding focused expectations. Do not revert the completed pressure-transport work or unrelated workspace changes. Git files contain earlier uncommitted work, so do not use whole-file checkout as the reversal mechanism.
