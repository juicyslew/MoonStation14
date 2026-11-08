# Autonomous flyer locomotion adaptation

Pinned SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`: `FlyingMobBase` composes
`InAir` for no-slip over-chasm movement; this is 2D, not autonomous 3D altitude
navigation. The source idle task selects a random destination, travels, then waits
2–7 seconds (40–140 ticks). Local NPC idle waits already use that same range.

Local `Flight.npc_cruise_height` (default 1.5 blocks, bounded 0.75–2.5) is a
Minecraft-only 3D adaptation, consumed from resolved `FlightComponent` by the
NPC behavior controller. Before steering, clearance is constrained to loaded,
collision-free space over nearby support; the controller performs no chunk loads.
Existing GroundNpcRoutePlanner remains the horizontal planner and the shared
GroundedHarnessWorldStep remains the sole collision/movement integration owner.
Altitude wishes are bounded inputs, not teleports; actual landing remains the
FlightSystem landing state. This intentionally makes no SS14 3D-flight parity claim.
