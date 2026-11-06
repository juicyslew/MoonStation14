# R39 — in-flight offline unload: reload failure

**Diagnostic failed; no readiness or M1 acceptance.** Preserve the used R39
world/logs and abandoned R38 configuration. Neither may be reused for another
native attempt. Production/default remains two substeps; M2–M6 remain paused.

## Observed boundary

The filtered selection was1/895. Compilation and isolated preparation passed.
The single native test completed with failure at fixture tick248: all three jug
UUID lookups were still absent during ordinary reload. The native frame and a
replacement body object were present, but final reconnect/identity/content
assertions were not reached.

| Fixture tick | Evidence |
| ---: | --- |
| 25 | Three original jug roots ready. |
| 26 | Ordinary20m/s upward nudge produced measured rise0.984771728515625m and native upward speed about19.36m/s. |
| 27 | Normal logout reached OFFLINE; held/body/Mind state recorded; fixture lease released. Original jug water amounts were500/600/700 cents. |
| 29 | Old frame absent; body and all three cargo objects removed as `UNLOADED_TO_CHUNK`, UUID lookups absent; physical witness chunks absent. Ordinary same-UUID holding payload was valid, with upward saved velocity about18.73m/s. |
| 29–48 | Existing quiet phase completed (20 observations,19 elapsed ticks). |
| 49 | Same frame UUID reacquired on a different object; native handle valid, observed upward velocity about16.85m/s. |
| 248 | Reload timed out: three cargo UUID lookups absent, original world-ledger owners retained; reconstructed body present, physical witness chunks ENTITY_TICKING. |

This establishes the **nonzero flight-at-serialization premise**, not successful
body/cargo continuity. First observed reload values are not a verified
pre-first-integration sample or exact saved/restored velocity equality. It does
not establish the cause of the owner's reported cargo loss or prove permanent
deletion; save/load payload and ownership ordering require investigation.

## Shutdown qualification

The server logged normal stopping and all dimensions saved. It also logged an
error encoding the GameTest failure's writable book: the long assertion exceeded
the permitted string size. Do not describe this as an error-free save/shutdown,
or as a reproduction/fix of the earlier R26 hang. Preserve the full error and
unabridged failure witnesses in raw evidence.

The fixture's reload-timeout branch now logs its full original state/witnesses
under `SABLE_CARGO_CHUNK_DIAGNOSTIC RELOAD_TIMEOUT` before throwing a concise
assertion with tick, frame UUID and all three cargo UUIDs. Compilation, focused
payload JVM5/5 and independent source review passed. This addresses the oversized
failure-book message only; no native save/shutdown repair is yet verified and no
cargo behavior or assertion outcome changed.

## Investigation boundary

Pinned Sable source's frame/plot serializer does not embed ordinary tracked
entities. The reviewed ordinary-unload branch does not call the destructive
`deleteAllEntities()` removal branch. Local `LooseItemEntity` delegates item
save/load to its superclass and adds loose-item settled/orientation/storage-point
metadata, not a separate world-position serializer.

Read-only inspection of retained post-failure entity-region data found the body
in the relevant world chunk but not these three cargo UUIDs in the bounded world
and virtual-storage locations. These are **post-failure files**, not an exact
unload-time entity-tag capture. Neither this nor stale ledger ownership identifies
whether cargo was omitted at save, written elsewhere, or failed load/admission.
Natural save eligibility and precise save/load payload witnesses remain the next
questions; no production cargo/physics/ledger fix is justified yet.

## Bound evidence

- Candidate `shuttle-feedback08-flight-offline-unload-20261016-r39`.
- Snapshot `82042ec9c270fa22b33d9112bed351f50f3d113dc94b1d9c65c7a2d10d8fd8de`.
- Frozen plan `GATE-PLAN-08-FLIGHT-OFFLINE-R39.json`.
- Reports root `build/agent-workflow/shuttle-relocation-sprint/reports/`.
- Receipt `shuttle-feedback08-flight-offline-unload-20261016-r39-gate-validation.json`,
  SHA-256 `b6e63707f83e4552b17d7bb404c30785952bb69039316abe6d924ce921032b1f`.
- Raw `feedback08-flight-offline-r39-native.log`,314,216 bytes,
  SHA-256 `f902cc7db65cbb312e4d1c057ce3eed06977c06188b47dd21428bdb9d71f4c8f`.
- Raw setup/flight/offline/cargo/release:1264–1272; held payload/unload/quiet:
  1276–1280; first observed reload:1282–1283; failure:1297–1299;
  stopping:1309; failure-book encoding error:1312 onward; dimensions saved:1361.
