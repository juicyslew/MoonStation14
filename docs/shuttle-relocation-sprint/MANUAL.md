# Freight-flight follow-up

Use the existing development setup and cargo shuttle; leave configuration unchanged. Use Tab completion for `<ship>`—no copied UUID or recording is needed. Run in clear space: gravity, momentum and ordinary collision/damage remain active.

1. Run `/ms14 shuttle_fixture bay_light <ship>` to light an existing bay. New fixtures already include the ceiling sea lantern.
2. Board the bay with a jug nearby, then run `/ms14 shuttle_fixture flight <ship> 90`. Keep your view still during combined turns and vertical changes; check orientation, walking and cargo stability.
3. To disengage early, run `/ms14 shuttle_fixture motion_stop <ship>`. This releases control; it **does not instantly brake** the ship.

The profile is slow, repeatable and jerk-limited, not a powered-thruster simulation. It does not spawn obstacles or guarantee bumps; contacts with existing terrain/objects use normal native physics. Expiry or the excursion guard also disengages control without wiping velocity.

Technical readiness is not owner acceptance. Post-repair pixels, Critical/Dead drift and two-client behavior remain separate outstanding observations.
