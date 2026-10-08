# Patient composition used by medical items

Verified SS14 pin: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

Human's relevant ancestry passes through `BaseSpeciesMobOrganic`,
`BaseSpeciesMob`, `MobDamageable`, and `BaseMob`; `AppearanceHuman` supplies
`HumanoidProfile.species: Human`. Pig's relevant ancestry passes through
`SimpleMobBase`, `SimpleSpaceMobBase`, `BaseSimpleMob`, `MobDamageable`, and
`BaseMob`. Its inventory species ID is not a humanoid profile.

`MobDamageable` explicitly sets `Damageable.damageContainer: Biological`.
The component-code default is nullable, not implicitly biological. Healing
requires Damageable composition and checks a non-null container against the
item's allowed containers; a null container passes that compatibility guard.

Local typed Damageable composition represents this consumed container field.
Human/Pig declare biological; Observer does not inherit organic patient policy.
Local HumanoidProfile represents the scanner-consumed species field only:
Human provides Human, while Pig retains unknown species. These additions do not
implement new anatomy, character customization or inventory species behavior.

Source index:
- `Resources/Prototypes/Entities/Mobs/base.yml:MobDamageable`.
- `Resources/Prototypes/Body/{species_base.yml,Species/human.yml}`.
- `Resources/Prototypes/Entities/Mobs/NPCs/{simplemob.yml,animals.yml}`.
- `Content.Shared/Damage/Components/DamageableComponent.cs`.
- `Content.Shared/Medical/Healing/HealingSystem.cs:TryHeal,OnDoAfter`.
- `Content.Client/HealthAnalyzer/UI/HealthAnalyzerControl.xaml.cs:Populate`.
