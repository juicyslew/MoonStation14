# Landing-step movement fix

Minecraft's collision resolver permits a grounded landing together with a bounded upward step, even when requested vertical movement is downward. The shared movement kernel previously rejected that legal result because its step eligibility only recognized initially-grounded movement. Eligibility now includes `wasGrounded || (requestedY < 0 && isGroundedAfterMove)`, while preserving finite-value, X/Z sign and magnitude, max-step, and ULP checks. Server rejection diagnostics capture before/after positions, velocity, requested/actual movement, grounded state, and max step; rejected movement is never retried or partially accepted.

Manual acceptance remains: test hurt landings and jumping across pipes on connected client/server worlds.
