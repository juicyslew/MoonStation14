# Knockdown posture projection

The resolved `knockdown` status declares both its marker and `knockdown_posture` behavior. `DefaultStatusEffectLifecycle` projects active status application, refresh, and reconciliation through `PostureSystem`; the existing posture attachment is synchronized normally and `PhysicalPosture.apply` updates native pose and collision dimensions. This applies to all sources of that status, including paralyze, shock, and slip, rather than being coupled to a particular source. Slip retains its independent launch, stun timing, and audio behavior.

Newly standing eligible characters use the normal forced-down transition. Refreshing an already-down character preserves orientation and voluntary/manual stand policy. Finite status duration is translated to the canonical overworld posture clock; an indefinite active status gates get-up without installing an indefinite posture deadline. Status removal or expiry never directly stands a body: existing deadline, consciousness/medical, timed get-up, and standing-space checks govern recovery. A stun-only status does not project prone posture.

## SS14 correspondence

Verified against `SS14-dev/space-station-14` revision `c9df5ef5d675b0d1d226828bddf6b78c28502d91`: `SharedStunSystem.TryUpdateParalyzeDuration` initializes knockdown, which reaches `StandingStateSystem.Down`; the already-downed path preserves orientation. This adaptation uses its resolved status behavior to invoke the existing authoritative local posture system, including synchronized posture and physical dimensions. It does not add a separate posture flag, packet, or renderer-only fallback.
