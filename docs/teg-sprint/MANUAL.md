# Owner manual checks

**Paused; not an owner-testing handoff.** Resume after the ECS overhaul and a fresh complete technical gate; see [HANDOFF.md](HANDOFF.md). The steps below are retained for later use, not a readiness claim.

Use a **fresh disposable Overworld world**: schema-1 pipe saves are incompatible. Do not phase-poll or reset an existing owner world.

1. `/ms14 teg_fixture build`, then `/ms14 teg_fixture start` once. Check automatic heater cutoff and R6 model fit; do not repeat-start to refill.
2. `/ms14 atmos_fixture build`; start `pressure_pump`, `volume_pump`, and `mixer` individually. After paid startup, observe actual steady flow; disable/zero and confirm silence, then restart without refill.
3. Hold the Analyzer: wheel air range 0–6, then use it on pipe lanes, device machines, and visible vent/scrubber connections; inspect ports and all gas rows including water vapor. Try offhand use, close/put away, optionally test through glass. Keep scrolling limited to the active source.
4. Compare connected pipe nodes: pressure, temperature, and composition should agree immediately; displayed moles scale with each node's volume. Open/close a manual valve to join/split its sides, and check that unrelated pipe layers remain separate except through a manifold.
