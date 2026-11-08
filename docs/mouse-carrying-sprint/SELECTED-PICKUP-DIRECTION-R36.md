# r36 gate corrections

r35 failed its fresh full native gate: 914 selected/914 total, three required
failures; focused medical JVM tests passed 8/8. Readiness and packaging did not run.
The bound raw log is `build/gate-selected-pickup-direction-r35.log` (SHA-256
`1ab5cc38c3f2656f9863813c773e66706fc6ab4332f602170e3b7eef0e8176aa`).

- The direction fixture now separates healthy, critical and recovery input into
  distinct ticks and checks matching applied-sequence snapshots. Critical damage
  and healing use `DamageSystem.applyHealthChange` and resolved mouse thresholds,
  not an attachment that physiological reconciliation can immediately overwrite.
  Facing assertions compare captured body/head baselines.
- Near-floor automatic-ray coverage now checks the actual full-look endpoint and
  `visibleCandidates`; selected UID diagnostics intentionally describe target
  range/LOS, not the obsolete current-ray requirement.
- The idle-direction fixture's failure showed all spawn positions loaded, but
  four positions in the next Z chunk were not yet entity-ticking (raw line 1169).
  Its bounded readiness wait is now 20 ticks instead of 8. The all-position
  predicate, motion assertions, observation clock and 70-tick timeout are retained.

These are unverified until the fresh r36 gate passes. No additional camera,
interpolation or same-holder escape-transfer fix is included. The scope and
upstream corrections in `SELECTED-PICKUP-DIRECTION.md` still apply.
