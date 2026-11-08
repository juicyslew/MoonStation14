# Observer prototype mapping

Verified against SS14 checkout revision `c9df5ef5d675b0d1d226828bddf6b78c28502d91`, `Resources/Prototypes/Entities/Mobs/Player/observer.yml`: `MobObserver` inherits `MobObserverBase`; the base composes `Incorporeal` and `BaseMob`, with walk 8 and sprint 12. The local typed observer base represents the movement defaults and incorporeal marker; `observer` inherits it and intentionally has no host mapping or unsupported `BaseMob` physiological components.

SS14's ignored gravity, air movement, and GhostImpassable collision behavior are not represented as fabricated prototype components. The Minecraft adaptation applies no-gravity/no-physics through the existing ghost mob harness. Ghost collision layers are deliberately not implemented.
