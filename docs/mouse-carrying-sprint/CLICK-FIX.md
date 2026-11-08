# Mouse primary-click pickup correction

## Owner-reported failure

Candidate `mouse-carrying-20261013-r2` did not pick up mice on normal primary
click. Its server-helper validation missed the live client route. Do not use that
candidate for this interaction.

## Cause and correction

`BodyHandClient` decided whether to pick up using only the loose-item pile target.
Living-body nomination happened after that decision, so clicking a mouse with an
empty hand routed to no action or background-block use instead of pickup.

Both primary-click entry paths now use the shared `PrimaryWorldGesture.click`
decision. An empty hand selects the existing pile first, then an eligible resolved
portable living target; its exact UUID goes through the existing
`PICKUP_SELECTED` request. Occupied-hand item targeting, medical routing and
modifiers remain unchanged. Server first-hit, reach, occlusion, lifecycle and hand
proof remain authoritative. No new packet or mouse-specific interaction exists.

## Validation and limits

Corrected candidate: `build/libs/moonstation14-0.0.1.jar`, 8,871,140 bytes;
SHA-256 `72AF53CCDA8655F48D2524CF7F12E0D219D1F9A82F9CED7D47DF1CEB5B49D6E2`.
Build log: `build/portable-click-candidate-jar.log`. No deployment, commit or push.

- Current full JVM: **3309 passed**, zero failures/errors/skips.
- Interaction readiness: **51 passed**, including the production gesture tests.
- Native hands selector: **225 passed**; portable pickup selector: **1 passed**.
  The added registered-player fixture uses the normal request service and checks
  that the same-UUID original mouse binds to its carrier.
- Independent source review confirms both callbacks pass the shared decision's
  action and target through `request()` to the existing server pickup handler.
- Full native: **892/893**, not green. The remaining
  `buttonpressopenslinkedpoweredairlock` failure directly activates a server-side
  device button and does not execute the changed client gesture code. Its cause
  is unresolved; no airlock/device production changes or assertion waivers were
  made for this fix.

The production client decision and normal server request path are covered, but a
connected client's physical mouse-button event has **not** been exercised. This
is not a claim of end-to-end connected-click verification. The original carrying
candidate's other cold-return/GPU boundaries remain unverified.
