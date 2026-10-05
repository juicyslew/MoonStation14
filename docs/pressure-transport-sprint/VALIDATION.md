# Pressure transport validation

## Automated acceptance

Final consolidated gate: `build/pressure-transport-validation/final-gate-03/`.

- **1,928 JUnit tests**, zero failures, errors or skips; 280 XML suites archived in `junit-xml/`.
- **76/76 selected GameTests**: the previous atmosphere/fire/explosion cohort plus the new pressure-transport fixture. This is not a full-namespace GameTest claim.
- Default unfiltered `compileGametestJava`, `assemble` and `git diff --check` passed.
- Independent reviews passed the pressure kernel, pressure-compatible LINDA, transaction/ledger publication, pressure admission, partial continuations and lifecycle cleanup, reservoir-terminal membership, local committed-reaction observations and numerical audit.

## Physical movement and costs

Final numerical evidence: `junit-xml/TEST-com.juicyslew.moonstation14.ms14.atmos.core.MonstermosPressureScaleTest.xml`.

- A connected 1,000-cell thermal-pressure scenario reduced its approximately 1,773 kPa pressure span to **31.07 kPa after one overlapping sweep**, **4.66 kPa after two**, and **0.168 kPa finally**. Temperatures remained nonuniform; this is not instantaneous room averaging.
- Matched-pressure isothermal extra-inventory stimulus reached **0.151 kPa** span within five sweeps. Reference comparisons use matched first-200 BFS seed admission; preview steps are already included in search work.
- Warm **200-node input / 200 included** kernel: **1.345 ms median, 1.728 ms P95**. A **1,000-node input / 200 included** costs **2.501 ms median, 4.022 ms P95**. These are informational headless costs, not controlled speedup ratios or live station TPS guarantees.
- Actual service fixture starts with equal **40 mol** inventories, source at 5,000 K and background at 293 K. Pressure span fell **1,565.45 → 0.220 kPa before GROUPS**. Under 59 source writes it completed 19 cycles and 197 patches, with zero pending ownership. It verifies realized inventory/energy accounting; ongoing writes leave a nonzero final span. See `gametest.log`, `PRESSURE_SCALE runtime`.
- The 124-cell trace-gas hallway and breathable-door replenishment passed their original propagation deadlines. Source and far-cell local fire events passed on the original three-cell stimulus, with sealed-system stoichiometry checks retained.

## Conservation and corrected observers

The repeated pressure/LINDA/group audit checks incremental and cumulative signed **and absolute** drift, including a cancellation-control regression. The producer loop measures realized add deltas before transport. Its focused audit counted 5,769 pressure faces, 4,016 LINDA pairs and 160 averaged members: **−25.44 J** residual versus a **9,945 J worst-case operation-derived allowance** (`build/cumulative-conservation-audit-gate-final.log`). Allowances count float arithmetic and explicit trace cleanup; no simulated energy correction was added.

Earlier failed gates exposed real admission/convergence defects: the absolute floor exceeded trace-room equilibrium and the old mole-delta preflight discarded pressure retries. Both are corrected; see `DECISIONS.md`. Failed-gate logs remain under `final-gate-01`, `final-gate-02` and focused repair evidence.

Fixture corrections retain gameplay invariants: float-storage/operation tolerances replace unjustified flat tolerances; finite ownership is checked directly rather than inferred from one vented cell's inventory; drying uses 20 helper ticks with unchanged production cadence; local chemistry is proved by committed events rather than exact face-volume metadata or net advected inventory. Explosion actor spawning is helper-relative, and the no-physics actor is isolated from vanilla hurt knockback; production explosion behavior is unchanged.

## Rebuilt artifact and remaining acceptance

`build/libs/moonstation14-0.0.1.jar`: **3,923,863 bytes**, built **2026-10-05 02:08:35 UTC**.

SHA-256: `4C7460BDB7E60D48030015DFD7667E1596D2BB9BCB5A8107E088B247F9081118`.

Automated implementation acceptance is complete. The owner reported the live result working well and requested sprint closure; see `HANDOFF.md`. This is qualitative live acceptance, not quantified station TPS, initial-front speed or a captured before/after diagnosis of the earlier room-wide jump. `LIVE-RETEST.md` remains available for future profiling. Keep 15 Hz admissions/resumptions and the shared soft 3 ms stage budget; atomic operations can overrun it. Wind/momentum, expansion work and generalized nonzero-ambient explosive decompression remain outside scope.
