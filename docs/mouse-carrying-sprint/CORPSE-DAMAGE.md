# Retained corpse damage: separate follow-up

Owner request: retained corpses must continue receiving appropriate physical and
environmental damage. This is separate from analyzer presentation and nominated
pull targeting. Gibbing and new weapons are not implemented by this task.

## Established parity

Reference checkout verified at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

- `Content.Shared/Damage/Systems/DamageableSystem.API.cs:61-179` does not reject
  dead mob state. Components, supported damage types, modifiers and cancellation
  remain authoritative.
- Melee and projectile systems use that shared API. `DamageOnLandSystem.cs:14-23`
  and `DamageOnHighSpeedImpactSystem.cs:25-53` apply configured impact damage
  without a dead-state rejection. Minecraft fall damage is a port adaptation,
  not a claim that SS14 universally implements Minecraft-style falling.
- `Content.Server/Atmos/EntitySystems/BarotraumaSystem.cs:201-257`,
  `Content.Server/Temperature/Systems/TemperatureSystem.Damage.cs:70-129`, and
  `Content.Server/Atmos/EntitySystems/FlammableSystem.cs:411-466` permit pressure,
  temperature and fire damage while their components and exposure conditions hold.
- `Content.Server/Body/Systems/RespiratorSystem.cs:80-120` skips dead bodies.
  `Content.Shared/Body/Systems/SharedBloodstreamSystem.cs:77-109` permits dead
  bleeding ticks but not blood regulation or bloodloss damage. Do not remove
  physiological death gates to fix physical damage.

## Local boundaries

`DamageSystem` owns the typed ledger. Positive typed transactions enter native
`hurt`; `observePost` commits mitigated damage and emits shared damage events.
Retained medical corpses have native zero health, so native damage admission
currently prevents those commits. Explosion collection also excludes native-dead
entities. Baseline import must **not** infer a fresh ledger from zero corpse health.

The fix must reuse that transaction/event boundary, preserving cancellation,
mitigation, exact entity identity, zero native health and Dead medical state.
It must not globally redefine `isAlive`, copy the native damage implementation,
add damage directly from per-source mixins, resurrect a body, or retain ordinary
unsupported native corpses. Physical-source eligibility must consume resolved
existing medical/component policy, not a mouse allowlist or another corpse flag.

## Implementation and pending acceptance

`MobStateSystem.acceptsPhysicalCorpseDamage` resolves server-side, nonremoved,
already medically Dead bodies. `LivingEntityMedicalCorpseMixin` snapshots that
eligibility at `hurt` entry and restores its scoped flag in `finally`, including
nested calls. It modifies exactly two native dead checks: initial admission and
later death/totem processing. The first lethal hit still follows ordinary death;
subsequent corpse hits retain normal incoming events, armor/effects, damage Pre/Post,
and the existing canonical ledger observer without repeating death handling.
Exact patched source: `build/moddev/artifacts/neoforge-21.1.224-sources.jar`,
`net/minecraft/world/entity/LivingEntity.java`. Source review verified
`actuallyHurt` reports mitigated damage independently of remaining health.

Barotrauma and explosion selection/knockback now admit retained non-alive medical
bodies using existing policy. Existing fire and thermal updates already lacked
the incidental alive gate; their damage now reaches the shared native seam.
No physiology or baseline-import changes were made.

R45 exposed an additional read-side bug: `existingOrVanillaBaseline` discarded
the existing ledger whenever native health was dead. Pressure cap evaluation
therefore saw zero damage after a ceiling-crossing hit killed the body. R46 reads
an existing ledger before considering native-alive baseline eligibility. With no
ledger, a dead body still yields no baseline and the query creates no attachment.
Changing cadence timestamps alone did not resolve this bug.

Fixtures now exercise actual harness falling/landing, native generic/projectile/
fire hits, canceled mixed typed mutations and exact accepted increments,
unsupported native-dead Cow rejection, and retained Pig pressure/blast damage.
Blast fixtures assert actual ledger increments, not only an `APPLIED` result.
Independent source review passed; source/GameTest compilation and 61 focused JVM
tests passed before the final fixture-only cooldown isolation correction.
These are not native runtime results. The fresh r44 serialized gate must verify
the fixtures before technical-ready publication; connected input/visual acceptance
remains manual. No mouse-specific fall fixture or new weapon feature is claimed.
