# Analyzer species adaptation

At pinned SS14 revision `c9df5ef5d675b0d1d226828bddf6b78c28502d91`, `MobMouse`
inherits `SimpleMobBase` and `BaseMobAnimal`; `MobPig` inherits
`SimpleMobBase` and `StripableInventoryBase`. Neither resolved composition
provides `HumanoidProfile`, which is what the upstream health analyzer reads, so
both animals would be Unknown there. Locally, a typed composed `Species` label is
deliberately added for Human, Mouse, and Pig so the analyzer can identify animals
without fabricating humanoid capabilities or inferring species from host identity.
Villagers continue to resolve the inherited Human prototype. The analyzer checks
resolved `Species` first, then retains the existing `HumanoidProfile` fallback.
