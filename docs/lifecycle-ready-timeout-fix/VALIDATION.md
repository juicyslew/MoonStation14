# Readiness timeout fix: validation

## Current state

An exact, authorized, live uncommitted character session that times out now uses
the existing clean-logout persistence path before disconnecting. Successful
parking saves `OFFLINE` and gives reconnect guidance. Invalid ownership/body
proof or failed persistence still fails closed; no replacement body is created.
Previously locked-out durable `ACTIVE` claims are not migrated or recovered by
this fix. The affected owner save was deleted before diagnosis.

## Automated evidence

- `cleanTest test build compileGameTestJava`: **2,956 tests passed**, zero
  failures, errors, or skips.
- Filtered native union `lifecycle-ready-timeout,lifecycle`: **76/732 selected**,
  **76 completed and passed**. Tags select fixtures; they do not provide isolation.
- Direct regression tests exercise the shared production timeout transition:
  parking before disconnect, durable `OFFLINE`, cold reload and same-character-ID
  reconnect, repeated timeout, and stale-epoch/owner rejection.
- Independent source/test review passed. Existing clean-logout/store/CAS behavior
  is reused rather than duplicating persistence or relabeling claims directly.
- JAR: `build/libs/moonstation14-0.0.1.jar`, SHA-256
  `240CB46517ABB63B0C8C5A8CD764BE8223D39CF14CBCA52BF35207DC018CD257`.
- Logs: `capturebuild/validation-logs/lifecycle-ready-timeout-unit-build.log`,
  `lifecycle-ready-timeout-focused-unit.log`, and
  `lifecycle-ready-timeout-native-focused.log`.

Full native validation was not rerun for this narrow lifecycle correction. The
preceding full snapshot completed 731 tests with four failures; see the
ChemMaster validation document. This focused result does not make that suite green.

## Remaining acceptance

Connected-client timeout/rejoin and a populated physical body/inventory round
trip remain unverified. Automated tests prove character IDs and harness retention,
not an observed world-entity/inventory reload. The original `BEGIN_TIMEOUT`
message remains unmatched without its logs; the confirmed `READY_TIMEOUT`
lockout path is corrected.

Manual check: keep a recognizable item, disconnect/rejoin, then restart and
rejoin. Confirm the same body/location and retained item. If a readiness timeout
occurs, preserve both logs and retry without deleting the world or player data.
