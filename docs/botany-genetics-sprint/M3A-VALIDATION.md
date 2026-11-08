# M3A stat-mutation validation status

This records the bounded M3A slice, not full sprint acceptance. Existing `PlantStatMutation`, `PlantMutationSystem`, prototype/profile, metabolism, harvest, extraction, and fixture owners are reused. Historical candidate results do not establish current whole-tree freshness after later M3 changes.

## M3A r5 gate evidence

Parent-verified validation report for `botany-stat-mutation-20261014-r5`: build **PASS**, focused JVM **57/57 PASS**, filtered `interaction,prediction` readiness **101/101 PASS**, and native **31/31 selected PASS of 854**. The native selector was `botany,botany-genetics,botany-chemical-mutation,botany-stat-mutation`. This is the M3A r5 candidate gate evidence; it is not a full-sprint or current whole-tree readiness claim.

Final fixture corrections assert the actual acquired chemical rather than assuming Vestine was selected, handle canonical nullable empty-hand presentation, and use a fresh query sequence value 4 with `Reason.OK` asserted before both hand lookups. Direct `GameTestInfo.fail(Throwable)` plus `BoundedGameTestWait` preserve actionable failures and bounded safe scheduling. Production behavior was unchanged by those fixture corrections.

The r5 manual check remains `/ms14 botany_fixture`, wait about 15 seconds, and inspect status for the modifier-3 operator preset and actual Viable toggle. Chemical harvest/extract/replant coverage is preserved. No GPU/visual or connected-client check, full-suite run, or owner acceptance is claimed. M3A's owner decision remains separate from this technical gate.

## Preserved history and boundaries

Failed r1–r4 native worlds/logs remain preserved; their failures are historical and do not block the passing r5 candidate. Earlier causes included fixture expectations/timeouts and are superseded for this candidate by the r5 evidence above. Do not clean up those artifacts.

The supported numeric/boolean/chemical runtime does not implement all 27 upstream capabilities. Unsupported source-ordered entries are diagnosed; Slippery is reported unsupported, not stored as a genetic record, and its record consumer remains unimplemented. Unsupported effect-body RNG consumption remains outside the parity boundary. Gas, species, pollen, special mechanics, full catalog, and assets remain unimplemented or incomplete. M3B gas/genetics work is underway, not complete. No full-sprint closure or handoff is recorded here.
