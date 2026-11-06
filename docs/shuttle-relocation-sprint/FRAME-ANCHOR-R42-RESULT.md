# R42 — fixture setup failed before anchor validation

**3/895 selected,3 completed,0 passed,3 failed,0 unfinished.** Compilation and
fresh preparation passed. This is a genuine failed diagnostic, not evidence
that native anchor hydration succeeded or failed. Retain R42 and R38–R41 intact;
no reused fixture or automatic retry. M1 remains unaccepted; M2–M6 paused.

## Failures

- `offlinebodyandcargosurviveordinarychunkreload`: tick22, singleton natural
  persistence watch already active. The fixture had incorrectly expanded the
  flight-only observer registration to concurrent stationary cases.
- `ordinarycargosurvivesnormalworldchunkunloadreload`: tick139, initial cargo
  readiness timeout.
- `flyingofflinebodyandcargosurviveordinarychunkreload`: tick133, initial cargo
  readiness timeout.

The new `restoredFromFrame()` requirement was mistakenly added to **fresh,
pre-unload** cargo readiness. Initial entities cannot already have restored a
saved anchor. It belongs only to the existing reconstructed-object readiness and
final verification. Restoring the original initial live/tracked/owner/settled/
zero-own-speed/three-sample guards does not weaken post-reload assertions.

No CARGO_READY/flight stimulus, actual unload, anchor initialization or hydration
was reached. The directory was prepared forSDK2, but the failures occurred before
the later effective-setting guard: do not claim an observed SDK2 anchor result.
The production fix remains unverified by native execution.

## Shutdown and next correction

Stopping and all dimensions saved were logged, but two oversized readiness
failure books failed encoding and their lectern states did not persist. No crash
marker appeared. Correct readiness reporting to retain full logs with compact
assertions, reusing the existing reload-timeout pattern; do not suppress failure.

Fixture-only correction must restore flight-only singleton observation, separate
fresh from reconstructed readiness, retain strict reconstructed provenance/
identity/content/local-point assertions and add focused phase/scope tests before
review and any fresh R43 submission. Stationary cases need no global flight
observer; their actual saved-anchor/readiness checks remain relevant regressions.
No production anchor change is justified by these setup failures alone.

Author correction now restores the initial phase's original guards and the
flight-only watch registration. Reconstructed cargo still requires saved-frame
initialization in both readiness and final verification; body reconnect keeps
its anchor/native-owner/local-point checks. Stationary cases do not dereference
or require the absent flight observer. Natural SAVE/READ evidence checks the
compound-type field token, not mere key text that would also match `MISSING`.
Readiness/reload failures share full logging and bounded assertions.

Compilation passed; focused checks **35/35** (fixture phase3, anchor8, watch7,
payload5, history1, body resolver11), zero failures/errors. The ASM phase tests
inspect actual fixture callsites. Combined tag preflight selects3/895; strict
preparer Python14/14 passed. R43's fresh target remains unprepared. Independent
review and a fresh diagnostic are still required; production hydration has not
yet been exercised by these corrected fixtures.

Final independent source/fixture/plan review passed. The cargo-only scene still
uses a structure-relative site, so its world coordinates can be far from the
explicit near-origin body/flight scenes. This is a retained baseline/premise
limitation, not an established cause of R42's setup failures or evidence of a
5,000-block flight envelope. Do not relocate or rerun a failed case merely to
obtain green evidence. R43 keeps this stationary baseline unchanged and reports
actual setup/physics outcomes independently.

## Bound evidence

- Candidate `shuttle-feedback08-frame-anchor-20261016-r42`.
- Snapshot `3402d1f3f7f2d6abfe5ba15902b2f3b660f04652e70e23a4bec74579c1f0112c`.
- Frozen plan `GATE-PLAN-08-FRAME-ANCHOR-R42.json`.
- Reports root `build/agent-workflow/shuttle-relocation-sprint/reports/`.
- Receipt `shuttle-feedback08-frame-anchor-20261016-r42-gate-validation.json`,
  SHA-256 `8a138846e72bcd98d047f9599888410c851d21c61de3f406669901bfb79b0715`.
- Raw `feedback08-frame-anchor-r42-native.log`,311,812 bytes,
  SHA-256 `aa159be04337b52adeeb4cdca0bacbcec97684f545b715d3cb95a154c081b42a`.
- Raw failures1267/1278/1301; progress1268/1281/1302;
  stopping1316; dimensions saved1407. Independent evidence review verified hashes,
  counts and the unexercised initialization boundary.
