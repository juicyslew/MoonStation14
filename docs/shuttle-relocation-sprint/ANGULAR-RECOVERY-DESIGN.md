# Airborne rotation and gravity alignment

Discussion record, 2026-10-28. **Implementation in progress; not owner-accepted.**
See [implementation and parity notes](ANGULAR-RECOVERY-IMPLEMENTATION.md) for code
boundaries and validation, and [manual checks](ANGULAR-RECOVERY-MANUAL.md).
Current priority: this behaviour first. Other connected-test issues are indexed in
[CONNECTED-TEST-FEEDBACK-20261028.md](CONNECTED-TEST-FEEDBACK-20261028.md).

## Separation and gravity are different

- Leaving an actual rotating support initially preserves its full angular
  velocity: yaw, pitch and roll where the supported body was rotating with it.
  For example, a co-rotating passenger leaving a 3-rad/s platform initially spins
  at 3 rad/s regardless of distance from its rotation axis.
- This is separate from inherited **linear** point velocity (`v + ω × r`), which
  the owner currently considers correct. Preserve that behaviour.
- **Entering a gravity field does not grant its owning ship's angular velocity.**
  Gravity changes acceleration and the eventual feet-down direction; it must not
  instantly copy the ship's spin or continuously force matching yaw.
- Existing support/contact carry is distinct from merely occupying a gravity
  field. Gravity-relative yaw controls do not require airborne yaw-lock to a ship.
- A full posture-dependent angular-momentum/inertia simulation is not required.

## Timed recovery

1. Inherit rotation on separation immediately. Start an unsupported/airborne timer.
2. Delay recovery slightly longer than a normal jump, based on actual jump airtime.
   Ordinary short jumps must not trigger a recovery episode or camera-mode change.
3. After the timer, smoothly damp free spin and align feet with the **current**
   gravity. Without a local field, use universal down as an orientation target;
   this must not create a gravitational force in otherwise gravity-free space.
4. Reach exactly zero residual free spin and a stable alignment within a bounded
   recovery window, rather than leave an indefinitely decaying mouse drift.

The timer, not a gravity-change event, starts recovery. Changing fields updates
the target without restarting the timer. A long jump may therefore undergo angular
drag even inside the same ship; this consequence is acceptable. Do not implement
the earlier suggestion to damp toward matching the gravity owner's yaw.

Entering an upside-down ship and recovering after ejection use the same alignment
behaviour. Gravity accelerates the body immediately; alignment need not finish
before falling toward the new floor. Preserve heading when straightforward;
near-vertical ambiguity can use a consistent fallback without elaborate machinery.
Controls stay gravity-relative; do not add manual three-axis body-orientation controls.

## Camera and landing

- Proposed comfort mode: stable third-person viewing while the character tumbles,
  then return to the body once alignment and spin permit comfortable play.
  Follow position without inheriting the body's uncontrolled spin.
- Inspect/reuse disposals and critical camera handling. If their common camera
  mechanics are duplicated, share those mechanics with this mode; retain distinct
  mode-specific behaviour and one camera owner. Current duplication is unverified.
- A mostly feet-down landing is normal. A substantially sideways/upside-down or
  strongly tumbling landing produces a brief, existing slip-like prone/knockdown
  and stun. Allow alignment tolerance; yaw alone is not a bad landing.
- Contact settles free-flight rotation into the supporting/prone behaviour; do not
  snap upright first. A prone landing hands back to normal prone-view behaviour,
  rather than trapping the recovery camera waiting for standing alignment.
- Keep ordinary fall damage independent and apply it once. Do not change its
  calculation here or erase fall history during reorientation. Its current
  height/velocity implementation remains to be checked.

## Boundaries and decisions still needed

Choose grace/recovery durations, alignment/spin thresholds, and camera transition
timing/defaults during implementation review. Verify pinned SS14 gravity coverage
and local composition before implementing fields; structure-owned coverage is an
expectation to research, not established parity. This is a deliberate 3D adaptation.

Window-view discomfort from genuine ship travel/rotation is separate. Window
screening/accessibility or a motion-sick trait is deferred. EVA stabilizers and
stealable rotation-reference machinery are future ideas, not part of this work.
