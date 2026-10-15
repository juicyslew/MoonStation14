# Mouse carrying review status

## State

**Owner-reported blocker:** clicking a mouse does not pick it up in the candidate
build. The first manual step failed. The automated/runtime-helper coverage below
did not establish the actual client click-to-pickup entry path; do not treat the
prior `ready_for_owner` report as proof that this interaction works. Correction
and entry-path regression coverage are documented in `CLICK-FIX.md`; that newer
record supersedes the build/check counts below. Physical connected-click testing
is still not claimed.

The cold-start authority and guard-coverage blockers from shared candidate
`mouse-carrying-20261012-r1` are resolved and independently reviewed. Current
automated checks and final test-fixture review pass. The revised shared candidate
is `mouse-carrying-20261013-r2`. Technical review
is not owner acceptance. No deployment, commit or push is claimed.

## Candidate build

`build/libs/moonstation14-0.0.1.jar`, 8,869,503 bytes;
SHA-256 `50FA507216EFE915F3DD63238AC46C90E1A1DB589D82134D6DFAFA91E7DFF16E`.
`./gradlew jar` passed; log `build/candidate-jar-build.log`. See `MANUAL.md` for
the concise owner checks. Independent shared-candidate review must be recorded
before requesting those checks.

## Implemented scope

- Same-entity mouse pickup, held/worn/world storage, release and root transfers.
- Resolved five-second escape capability, shared timed progress/input provenance,
  interruption and bounded autonomous post-escape fleeing.
- Original-body throwing and disposal intake/transit/outlet, including a
  registered-player mouse binding/camera fixture.
- Persisted original containment flags and exact loaded-root recovery.
- Stomach recipe integration through the existing reagent engine; products and
  sounds only, not new reaction heat/gas/explosion effects.
- Food-pile acquisition randomness and unpowered airlock lamp rendering fixes.
- Correct attributed SS14 mouse icon/critical/splat frames and existing R2 held
  model adaptation.

## Evidence

- Earlier full native: **892/892**,
  `build/gametest-mouse-carrying-final-full-current-r2.log`, before the final
  cold-restoration failure-cleanup changes. This is not current global proof.
- Current full native: **892/892**,
  `build/gametest-cold-full-safe-current.log`.
- Full JVM after failure cleanup: **3306 passed**, no failures/errors/skips.
- Readiness `ui,interaction,prediction`: **116 tests / 23 classes**, no failures,
  errors or skips, `build/mouse-carrying-final-readiness-current-r3.log`.
- Storage/equipment regression selector: earlier **80/891** passed; latest
  changed native fixtures plus portable escape: **26/892** passed.
- Final cold recovery/stomach reaction selector: **9/892** passed,
  `build/gametest-cold-stomach-safe-current.log`.
- Independent CPU image review confirmed nine coat/state combinations and icon
  bounds. Contact sheet:
  `build/minecraft-junit/build/reports/living-carry-preview/mouse-appearance-contact-sheet.png`;
  SHA-256 `25DF763E7DDB643E578E72714F1E66E790F4AB2AA2D2529686FA71D2F4DE5728`.
  Its header clips; this does not establish native GPU mesh/clipping behavior.

Earlier failures are retained: portable validation rejected supported nonportable
legacy belts; this was fixed without admitting oversized legacy grid sources.
The final APC-light failure was fixture timing: emission state was ready before
asynchronous light propagation. The fixture now awaits propagation within its
existing bound; production power and positive/extinction assertions are unchanged.

## Resolved review findings

1. Cold OFFLINE relocation now restores only the exact persisted claim under a
   current-primary lease, after loaded CHARACTER identity/binding and physical
   custody proof. It preserves durable generation, identity, OFFLINE state and
   timestamp. Fresh-empty-context unit/native fixtures cover this path.
2. Unreadable oversized and dual-component hosts containing raw portable keys
   are tested through the production gateway: rejection conserves the original
   key, body, custody and flags. Nonportable legacy behavior remains unchanged.
3. A failed second custody proof or definite empty relocation rolls back only
   newly restored authority, then unregisters only this attempt's harness.
   Capability/identity fences preserve preexisting authority; the generation
   counter is not rewound. Independent review passed this inverse. IOException
   has an uncertain store-commit boundary; no atomic crash rollback is claimed.

## Prior full-suite failures

The earlier **890/892** rollback run failed:
`stationarycriticalcrushcontactshocksoncepersolidification` (face-tangent shock
delta) and `defaultblastprimesfinitereactivecellwithoutinstantheating` (unchanged
mixture after 80 ticks, queued atmosphere work). The previous full run failed
different airlock assertions. Isolated airlock checks passed **8/892**; that does
not clear full-suite failures. Neither root cause nor a dependency on mouse
carrying is established. Preserve failure logs and do not waive assertions or
change unrelated production systems without evidence.

The final full run passed **892/892** without production airlock/atmosphere
changes. Airlock assertion messages gained failure-only diagnostics. This pass
does not explain the prior cross-domain failures; their cause remains unknown.
An intervening full run crashed on a cold-fixture assumption that joining an
entity implied ticking it on that game-time bucket. That inference was removed:
immediate NBT is checked exactly, and the resolved digestion rate is checked
deterministically through the existing production metabolism consumer. Delayed
assertions now fail the GameTest rather than crashing the server runner.

## Unverified boundaries

Connected-client/GPU held/world appearance, pointer behavior and full cold-return
interaction remain owner-review boundaries. Internal/native fixtures do not
establish real peer latency or GPU presentation. Existing unrelated working-tree
reference-source deletions, including 16 top-level `net/...` paths, were left
untouched and are outside the gameplay implementation scope.
