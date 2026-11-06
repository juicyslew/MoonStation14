# Boarding response — focused owner check

Use these steps **only after the corrected candidate's readiness PASS**. The
broader checklist remains paused. Normal launch, fresh world, no custom run
directory or configuration changes.

1. Check ordinary ground movement, releasing forward, and jumping. Build the
   usual `/ms14 shuttle_fixture build`; confirm six substeps in `status <id>`.
2. Under that command prefix, use `motion <id> 0 0 2 0 60`. Attempt one jump into
   the bay while the ship moves away, as in the failed test.
3. Immediately check forward/release/jump response after the attempt. Stop on
   delayed controls or repeated corrections; note the approximate action/time.

Do not proceed to jug, high-spin or camera checks while this blocker remains.
The correction targets repeated prediction-history loss; the first boarding
contact and connected responsiveness still require observation, not assumed success.
