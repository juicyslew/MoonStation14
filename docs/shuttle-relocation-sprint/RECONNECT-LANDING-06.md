# Falling-shuttle reconnect / landed cabin diagnostic

The tagged `shuttle-falling-reconnect` fixture now holds the owner offline for
up to 240 ticks. It requires repeated fresh Sable floor-contact records on the
cabin frame, observed downward motion, near-zero vertical frame velocity, and
projects the actual authored cabin floor block under the body through the
settled frame pose. The floor's transformed bottom face must meet the authored
stone terrain top, and the body's projected footpoint must remain at that floor's
transformed top before normal reconnect/Ready. The frame pose origin is its
native physics center of mass, not the cabin floor; the assertion deliberately
does not compare that origin to the terrain height. Existing assertions retain
the same body, Mind, hands/item token, active lifecycle claim, and frame storage-
point continuity across Ready; they do not teleport the saved body pose.

After Ready, the same joined actor aims at the hatch's live projected point and
sends a registered `ACTIVATE_WORLD` `BodyBlockUseRequest`; the regression checks
the real door's exact resolved demand, fresh full meter, powered/closed phase,
retracted bolts, projected frame ray, and body reach before the request, then
waits for the matching epoch/sequence `BodyBlockUseResult` before asserting the
result and opening phase. Payload diagnostics retain concise type IDs and
correlated result details. After the normal opening/auto-close cycle it
approaches via the rejoined actor's registered view and forward-wish lifecycle
intents, checking projected eye ray, reach, door phase/bolts, and fresh paid
delivery before each input. It records the actual CLOSED-to-OPENING transition
only when the fresh Sable collision result captures contact with the exact hatch
half on the cabin frame, stops with a neutral joined intent, and
does not send a second intent in that tick. It completes only after the neutral
sequence is acknowledged and an ordinary owned physical step is observed; later
door phases do not fail the witness. This exercises native movement
and `AirlockBumpSystem` without a post-tick direct `Entity.move` that bypasses
the physical-step budget or approaches the adjacent wall. Neither path calls door
activation directly. The fixture establishes power readiness for the alarm,
but door-specific delivery is observed independently and failure identifies
that gate separately. R3's correlated `DENIED` was identified in R4 as
`issued-hands=false`; authority epoch, hand compatibility/token/revision,
can-act, and target probe were otherwise valid. `BodyHandStateService` issued
owner snapshots on queries and explicit republishes, while lifecycle Ready only
committed the session and sent `Commit`. Lifecycle Ready now publishes through
the existing owner-snapshot service after sending `Commit`, then verifies the
exact body's current epoch is issued. Publication failure logs a bounded reason
but does not revoke Ready, replace the body, or bypass hand validation. The
first-join/reconnect fixture now waits for production issuance instead of
forcing `requireFreshQuery` itself. `BodyHandClient.onSnapshot` already calls
`reconcile()` before strict account/body/epoch policy acceptance, so the ordered
Commit-then-push needs no protocol or client behavior change; policy coverage
checks new-epoch acceptance and stale old-epoch rejection. The owner's connected
post-rejoin hatch outcome after this fix remains unverified.

The projected landing-witness source passed `compileJava compileGameTestJava`
in r2. R3 added correlated BodyBlockUse response waiting and bump transient-phase
witnessing; R4 admission diagnostics located the missing owner snapshot issue.
R5 source and focused test validation are recorded in
`build/shuttle-feedback-06-r5-author-hands.log`: `compileJava compileGameTestJava`
and focused JUnit selection passed **68/68** (BodyHandStatePolicy 14,
BodyHandClientPolicy 41, LifecycleCharacterSessionControl 13). Native execution
is pending the central serialized gate; this fixture does not establish connected
rejoin presentation or the owner's post-fix hatch result.
