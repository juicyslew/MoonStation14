# R26: fresh offline-body persistence diagnostic

The owner authorized continuing with a fresh candidate and fixture while
preserving R25. Gameplay/test implementation and the proof contract in
`OFFLINE-BODY-CHUNK-DIAGNOSTIC.md` are unchanged. Focused fixture compilation and
independent source review passed; the combined native result remains unknown.

## Preserved R25

Candidate `shuttle-feedback08-offline-unload-20261015-r25`, snapshot
`5087c09c5a1f0f5a366b08429eacced616d4c1bb5d5cdf06c7b537301bccf084`, was
interrupted during startup. Its preflight selected2/893, but there is no completed
test aggregate or validation receipt. Do not label it a gameplay pass/failure.
The recorded lead health reason was “lead stopped after bounded recovery; author
action required.” The recorded runner processes were subsequently absent.

Leave its fixture, plan, log and workflow records untouched. Baseline:

- `feedback08-offline-unload-r25-native.log`:41,519 bytes, SHA-256
  `96f4e810f7a33f3a05342af8a74c92ebca0a8f8d825f062bacea1655795e6d25`.
- `GATE-PLAN-08-OFFLINE-UNLOAD-R25.json`:3,583 bytes, SHA-256
  `ac7b4e229da9f986d132de9a6f35f0177b256bc93ba8ab6b3e66a6d9d5122f8d`.

## Fresh scope

New plan `GATE-PLAN-08-OFFLINE-UNLOAD-R26.json` selects the combined offline-body
case and the changed shared cargo-only regression, expected2/893. Fresh fixture:
`build/gametest-shuttle-feedback-08-offline-unload-r26`; fresh native log:
`build/agent-workflow/shuttle-relocation-sprint/reports/feedback08-offline-unload-r26-native.log`.

Require actual body/cargo/ship unload before explicit reload, then normal
reconnect with preserved body UUID, Mind, hands, held token/full stack, cargo
snapshots/ledger and exact replacement-frame support. Reconstructed Java objects
must differ from unloaded originals; a reconstructed body may exist before login.
Source-derived jug quantities are not separately observed numeric reagent values.

No production or Sable changes, live configuration edits or new physics hooks.
Default2 remains. This is stationary, same-process coverage, not flight, process
restart, client/Creative proof, owner-loss repair, M1 acceptance or readiness.
