# R47 — committed serialization works; post-hydration motion unresolved

**Filtered 3/895 selected, all completed: 2 passed, 1 failed.** Compilation and
fresh default-two preparation passed; effective SDK2 reached. Both stationary
cases passed. Flight failed its unchanged strict reconnect oracle. No readiness,
M1 acceptance or M2–M6 advancement. Preserve this fixture and all earlier evidence.

## Established boundary

Flight body `695c26b0-7746-497f-b81f-2ac1f73ed487`, frame
`1a980776-b6ff-4ed6-8249-46f1cf40b145`:

- Original native movement produced a qualifying commit. At callback gameTime127
  and natural SAVE129, world position was exactly unchanged at
  `(2353.0001041676737, 301.98477716305854, 1703.000023739282)`.
- Both witnesses retained composition sequence8, localY128.99999999999997 and
  exact native owner. The callback owner was registered/unremoved; at save it
  was removed/unregistered. Physical chunks were non-entity-ticking throughout.
- Natural serialization used **FRAME / committed-unloaded**, preserving that
  local point rather than the newer-pose inverse. Initialization placed the body
  at worldY303.7653305659, on the reloaded deck with the jugs.
- First post-hydration COMMIT: localY129.1539001619473, worldY303.91923072784704.
  The rejected-world-step warning reports requested ownY−0.076832, measured
  ownY−0.673767, inheritedY+0.827667, net worldY+0.153900. Native movement precedes
  the motor's exception catch; that catch does not roll back physical position.
  The own delta was measured immediately after `Entity.move`, before carry;
  net+.153900 must not be described as the displacement of `Entity.move` alone.
  Its existing collision trace selects steel at storageY131 with a downward
  response near−.596935, accounting for that step's excess negative own travel.
- Final body: localY129.54547187714437, worldY309.3050041032009, grounded, alive,
  restored, exact native frame/tracking, normal gravity/physics. Listed UUID,
  binding/Mind/ACTIVE profile/hands/held token/stack checks remained true.
  Reported local distance **1.4983535745572876m > .12m** still failed.

The prior bad-local-at-save failure is fixed in this bounded run. This does not
establish correct post-hydration motion or identify its exact collision cause.
All three flight cargo roots initialized; each completed50 post-JOIN samples
with0 spills. No trace error/truncation; normal stop/dimension saves observed.
Fresh missing `server.properties` and `create:flywheel` startup errors remain
separate findings, not a diagnosed cause or an explanation for R26.

## Comparison-basis caveat

The fixture records `savedFrameStoragePoint` from `currentActorPoint` at OFFLINE,
which inversely projects through the then-current provider pose. It is not the
movement-time committed point. The original body's position lagged the jugs by
about.952m; this is consistent with a baseline below129, but that baseline was
not explicitly logged. Do not report inferred baseline coordinates as measured.

Thus1.498m is not a clean measure of new post-load drift. Nevertheless, final
current localY is about.545m above the saved committed129, still beyond.12.
Merely replacing the baseline would not make this run pass. Do not relax the
oracle, substitute the baseline silently, or mistake world rise of a flying
frame for relative drift. Live current-pose queries and movement-time composition
are distinct observations; neither may be swapped opportunistically.

## Next bounded investigation

Use existing lifecycle diagnostics and native `MoveResult`/owned-move description
to observe the first post-hydration steps. Distinguish serialized anchor,
hydrated placement, pre/post-native move/carry position, selected collision pose,
requested/measured own displacement, inherited displacement and final current
point. Add explicit original OFFLINE expected point and final comparison facts
without changing the success predicate. Keep diagnostics bounded, opt-in,
error-isolated, non-persistent and observational; reuse the existing per-level
diagnostic scope rather than adding ownership registries or physics callbacks.

No provisional carrier reset, tracking repair, extra physical attempt, freeze,
gravity/velocity change, new solver or recovery placement is justified by R47.
Source review must first identify which necessary witnesses are already present.
Any further native diagnostic needs reviewed code and a fresh frozen plan/target;
R47 must never be reused or automatically rerun.

Subsequent source investigation confirmed that the first rejected step's existing
owned-move description already contains the relevant collision/pose trace. What
is missing is the bounded sequence of later, potentially successful attempts.
R48 implementation therefore reuses that description at the existing shared
completion seam, with raw pre-own/post-own/post-carry positions; it does not add
a new collision probe. Fixture logging exposes the original OFFLINE baseline
and timeout expected point without changing the success predicate. Runtime
instrumentation is now implemented and compiled. The actual actor-owned policy
is tested through FRAME/scope/living gating, failed/successful hydration,64
samples plus one truncation event, disabling and read reset. Compiled assertions
cover actual native-completion ordering, phase skips, observer scope/armed
branches, pending-save capture exclusion and bounded diagnostic-error handling.

Author evidence: **70/70 across nine JVM classes**, zero skips/failures/errors,
all nine XMLs verified by parent (state23, capture10, lifecycle trace7, native
wiring2, phase4, watch7, payload5, history1, body resolver11). Exact offline
preflight **3/895**, strict preparer Python14/14, scoped diff checks and independent
final source/test/plan review passed. R48 target remains absent and unprepared
at submission preparation. No R48 native result or new continuity claim exists.

### Test-source incident and reconstruction

An implementation worker replaced the lifecycle trace test class, dropping four
earlier tests while adding two. Parent review caught this before submission;
the resulting65-test green run was not accepted. Bounded recovery did not recover
the original source: workflow records dirty-file hashes, not bytes; checked
task-local saved records/project snapshots also did not yield it. Original R47
file SHA-256 was
`9ca19caac7ec0c1f16931c58c7f474c667eb497bab1711e897d1f9a250aaeee7`.
**Original source recovery remains unresolved; this is not exact restoration or
claimed historical equivalence.** No denied cache/artifact access was used.

The current class retains the two R48 tests and reconstructs five checks from
documented R45 responsibilities: body-super initialization/trace ordering,
natural body save delegation, SAVE-before-pending/capture ordering, SDK observer
gating/identity reporting and bounded nonforcing diagnostic error handling.
Independent review accepted their substantive current coverage after branch
assertions were checked against actual compiled project classes.

Important contracts: `BEFORE_BODY_SUPER` intentionally precedes initialization;
failed initialization skips actual super tick and `AFTER_BODY_SUPER`. The SDK
observer logs every armed actor and **reports**, rather than requires,
`nativeOwnerIsCallback`. Filtering mismatches would hide ownership loss.
Serialization authority remains separately exact-owner qualified. Production
was not changed to fit mistaken reconstructed assertions.

Intermediate failing test XML/HTML remains under the reports root:
`r48-reconstructed-guard-failure/` (6/7 passed, selector error; XML SHA-256
`43f833bd40f49cba6fb7fc14fe9d11376879a4a8cfcc17073d9c46c4f73aba13`),
`r48-refined-guard-failure/` (5/7 passed) and `r48-final-guard-failure/` (6/7
passed), the latter two exposing test branch assertions. An earlier5/6 XML had
already been overwritten and was not regenerated. Current70/70 evidence follows
the corrected assertions and does not erase these failures or recover lost text.

## Evidence binding

Reports root: `build/agent-workflow/shuttle-relocation-sprint/reports/`.

- Candidate `shuttle-feedback08-frame-composition-20261016-r47`.
- Snapshot `2354e6fa4e00235938ccf9e867160cd7d9086c47dbd18d8c4ec9b2dedbe81b38`.
- Receipt `shuttle-feedback08-frame-composition-20261016-r47-gate-validation.json`:
  SHA-256 `4fc664a16b72af306f645790f202da4c5a29d5ddd537154dfb99443a21046b7a`.
- Raw `feedback08-frame-composition-r47-native.log`,371,128 bytes:
  SHA-256 `1fc98215d016f9215f9ad64e597c0e7b9310c9c732213b7d21804e8527e4b24b`.
- Stationary passes1307/1328; flight callback1354, save1355–1356,
  initialization1370, post-load commit1375/warning1376, final facts1388/fail1390.
- Independent receipt/hash/log review verified the binding and2-pass/1-fail
  boundary. Read-only source investigation identified the comparison-basis caveat
  and the unaccounted post-hydration displacement, not a complete causal verdict.
