# R30: stock-only 80 m/s penetration reproduced

## Bound evidence

- Candidate `shuttle-feedback08-stock-only-20261016-r30`, snapshot
  `4a5ab976bf665ffa06b56e94ee288c83b59e780ea0ebb524c42e43041b6b8ce1`.
- Receipt `shuttle-feedback08-stock-only-20261016-r30-gate-validation.json`,
  SHA-256 `0ad3616c938b882688cb083b1dccc8fd32f6835f0062698032f100ff74e7a899`.
- Raw `feedback08-stock-only-r30-native.log`, 54,402 bytes, SHA-256
  `cb91e4056483eb078befb63bd39b376969dcfbb624078eb3f673e3ee761cc1d7`.
  Receipt/raw are under `build/agent-workflow/shuttle-relocation-sprint/reports/`.

Independent read-only evidence review verified the terminal results, runtime
identity, composition/admission and orderly shutdown against these bound files.

## Actual outcomes

Full **control catalogue** selected/completed 2/2: **1 passed, 1 failed**.
Neither receipt parser null counts nor its absent all-passed marker override
the native terminal records and aggregate. This was not the MS14 full suite.

| Upward launch | Result under the unchanged oracle | Evidence |
|---|---|---|
| 20 m/s | PASS: ascent/descent/contact and ten settled samples. Maximum penetration **0.155389 m**; no two-consecutive-sample deep-penetration failure latched. This is not zero penetration or speed certification. | Raw 126–143 |
| 80 m/s | FAIL: >0.10 m penetration first at flight sample **247**, confirmed **248**; maximum **0.989128 m**. Later whole-hull escape through the four-layer support slab. Final sample **268**, with **20** post-failure observations. | Raw 159–201 |

Both cases reached valid ten-sample grounded admission, near-identity orientation
within the unchanged 1e−6 guard and actual native velocity admission at two
substeps (115–127). Pre/post state/shape/property captures agree: 452 parts/cells,
451 stone at mass 2 plus one glass at mass 1, total **903**, equal to the native
tracker. Physical fingerprint remains
`c6cdd7a22cf9c4098132d63c9e03fca1ef79d50e9022426c238f7ea44d9c4bdd`.
Residency logs show 20/16 physical-world pad chunks and two allocated storage
footprint chunks per case, without vanilla tickets for virtual storage (112/124).

The independent project loaded stock Sable 2.0.6, its required dependencies and
the small control mod, **not MS14**. Exact versions and actual loaded JAR digest
were verified again; official Sable SHA-256 is
`9c199157843bf953a47dff625fc5bb41c01a90538d35ad18e6996bbcb8a617c2`.
The root mod, its mixins and access transformer were excluded. The neutral-package
shared helpers only observe; public stock assembly and native body APIs own
physics. An empty mixin-config query does not mean Sable's own mixins were absent.

The cases completed in **3.855 s** (203–205). All three dimensions saved normally
(217–220); shutdown capture cancelled on normal stop (221), without sampling.
Process exit **1** reflects the real trajectory assertion failure, not a timeout;
tree cleanup was `not_needed`. Overall receipt remains **FAIL**, not readiness.

## Conclusion and limits

**MS14 integration is not necessary for this vanilla-shell 80 m/s penetration
and slab escape.** That is the requested isolation result. It does not prove
Sable is defective, violates a promised collision-speed guarantee, or that MS14
cannot additionally contribute to the original furnished-hull problem. No such
version-pinned speed/CCD guarantee was found in the inspected documentation.
R16's MS14 material/state/mass composition differs; this is not an exact matched
MS14-present/absent comparator. Use this same vanilla fixture on both sides if
that narrower attribution is needed.

R30 did not reproduce R29's shutdown stall after local support/residency fixes.
Multiple premises changed, so clean shutdown is not unique causal proof and does
not diagnose R26. Preserve R28/R29 failures. Do not automatically change older
fixtures, adopt ten substeps, rerun a valid check or modify Sable internals.
Production/body/cargo/lifecycle/client acceptance remains separate; M1 is still
unaccepted and M2–M6 paused.
