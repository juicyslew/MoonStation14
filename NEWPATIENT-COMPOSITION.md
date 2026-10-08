# Patient composition additions

Upstream comparison verified against `space-station-14` revision
`c9df5ef5d675b0d1d226828bddf6b78c28502d91`:

- Human's relevant chain is `MobHuman -> BaseSpeciesMobOrganic -> BaseSpeciesMob -> MobDamageable -> BaseMob`, with `AppearanceHuman`; Pig's is `MobPig -> SimpleMobBase -> SimpleSpaceMobBase -> BaseSimpleMob -> MobDamageable -> BaseMob`.
- `Resources/Prototypes/Entities/Mobs/base.yml` defines `MobDamageable` with `damageContainer: Biological` (and its health thresholds). Local `Damageable` represents only healing enrollment/container; omitted `damage_container` remains empty and does not invent a default container.
- `AppearanceHuman` carries `HumanoidProfile.species: Human`. Pig instead has `Inventory.speciesId: pig` and no humanoid profile; local scanner therefore deliberately reports `Unknown` for Pig rather than inferring species from native entity type.

Human and Pig explicitly compose `Damageable` with `Biological`; only Human composes the scanner-consumed `HumanoidProfile` species ID. No unsupported appearance, inventory, resistance, or damage-threshold data is modeled here.
