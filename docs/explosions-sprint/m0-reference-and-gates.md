# Explosions sprint — M0 reference and gates

## Status

Owner-approved baseline: SS14 is the behavioral reference (not a 3D parity claim); structural floors, walls, and ceilings share a breach rule; target direction is nearly all destructible except explicit immunity/protection; begin modded-first and defer vanilla if complex. Owner authorization of that baseline is distinct from approval of local numeric balance, immunity mapping, or world-protection policy. The current conservative implementation is test-only; it does not authorize gameplay entry or a public trigger.

Labels below distinguish pinned source facts, owner-approved direction, and implementation choices for a test-only fixture. Fixture values are not owner-approved gameplay balance.

## Pinned reference evidence

Audited SS14 checkout: `C:\Users\William\Documents\SS14-dev\space-station-14`, HEAD `c9df5ef5d675b0d1d226828bddf6b78c28502d91` (short `c9df5ef5d6`). Source contracts:

| Source | Observed contract |
|---|---|
| `Content.Server/Explosion/EntitySystems/ExplosionSystem.TileFill.cs` | Distributes event budget across tiles in half-slope increments (`stepSize = slope / 2`); cardinal paths use two iteration steps and 2D diagonal paths three. |
| `Content.Server/Explosion/EntitySystems/ExplosionSystem.Airtight.cs` | Airtight entry is delayed until a health/damage-derived tolerance threshold is crossed, not charged a fixed universal obstacle cost. |
| `Content.Server/Explosion/EntitySystems/ExplosionSystem.Processing.cs` | Floor peeling is separate: intensity × scale probability, successful peel can reroll/reveal layers up to a break cap while reducing intensity. This is not the structural rule for Minecraft floors. |
| `Content.Shared/Explosion/ExplosionPrototype.cs` and `Resources/Prototypes/explosion.yml` (`Default`) | The class field initializers define break-curve defaults `[0, 1]` at intensities `[0, 15]` and reroll reduction 10. The `Default` prototype defines damage per intensity (heat 5, blunt 5, piercing 5), break-curve points `[0, 0.5, 1]` at intensities `[0, 10, 30]`, and reroll reduction 20. |
| `Content.Shared/Explosion/Components/ExplosiveComponent.cs`, `Resources/Prototypes/Entities/Objects/Weapons/Throwable/grenades.yml` | Component field defaults are total intensity 10, slope 1, and maximum intensity 4. `ExGrenade` (in `grenades.yml`) is a useful small reference fixture: total intensity 120, slope 3, maximum intensity 10, vacuum false. |

Source links (pinned checkout above): [tile fill](https://github.com/space-wizards/space-station-14/blob/c9df5ef5d675b0d1d226828bddf6b78c28502d91/Content.Server/Explosion/EntitySystems/ExplosionSystem.TileFill.cs), [airtight handling](https://github.com/space-wizards/space-station-14/blob/c9df5ef5d675b0d1d226828bddf6b78c28502d91/Content.Server/Explosion/EntitySystems/ExplosionSystem.Airtight.cs), [processing](https://github.com/space-wizards/space-station-14/blob/c9df5ef5d675b0d1d226828bddf6b78c28502d91/Content.Server/Explosion/EntitySystems/ExplosionSystem.Processing.cs), [prototype](https://github.com/space-wizards/space-station-14/blob/c9df5ef5d675b0d1d226828bddf6b78c28502d91/Content.Shared/Explosion/ExplosionPrototype.cs), [Default prototype](https://github.com/space-wizards/space-station-14/blob/c9df5ef5d675b0d1d226828bddf6b78c28502d91/Resources/Prototypes/explosion.yml), [explosive component](https://github.com/space-wizards/space-station-14/blob/c9df5ef5d675b0d1d226828bddf6b78c28502d91/Content.Shared/Explosion/Components/ExplosiveComponent.cs), [grenades / ExGrenade](https://github.com/space-wizards/space-station-14/blob/c9df5ef5d675b0d1d226828bddf6b78c28502d91/Resources/Prototypes/Entities/Objects/Weapons/Throwable/grenades.yml).

SS14's 500 iterations, roughly 205783 area, 100 tiles/tick, and 7ms/tick are upstream defaults, **not** local safety caps. The cited algorithm is 2D and its ordering/random generator are not requirements for a 3D clone. Do not turn these facts into parity claims or import them as Minecraft-tuned values.

## Test-only implementation choices and open gameplay decisions

These are historical M0 fixture choices, not current prototype data, gameplay acceptance, or owner approval of numeric balance. The later M3 implementation migrated explosion/source metadata into registered data prototypes; current distinctions and validation are documented in [M3](m3-player-grenade.md).

- No public trigger, network exposure, usable item, or production gameplay entry. Exercise only through trusted local tests.
- Historical M0 used SS14 `ExGrenade` source values 120/3/10 as an **uncalibrated** starting fixture, not a calibrated 3D radius or gameplay tune, with 128 sampled cells and 32 expansion iterations. These are superseded M0 fixture details: current source prototype and planner limits differ. The 120/3/10 values are SS14 `Explosive` source data; local `MAX_VISITED=256` and ordinary tolerance 2 are hardcoded planner choices, not SS14-sourced values.
- Ship a typed `Default`-inspired prototype fixture (heat 5, blunt 5, piercing 5; break curve 0/10/30 -> 0/0.5/1). The curve is currently cosmetic data; decorative floor peel is deferred.
- For initial structural fixture proof, use equal floor/wall/ceiling tolerance derived from nominal typed damage: 30 structural damage divided by 15 typed `Default` damage per intensity gives 2 intensity. This is a proposed fixture threshold only, **not** SS14 canonical block health, owner-approved balance, or a parity result. No block-hardness model and no cumulative partial block damage are used; this is an explicit deviation from SS14's health/damage-derived tolerance behavior.
- Initially whitelist known mod station blocks for destructive proof. This limited fixture whitelist is not a permanent immunity/ownership policy: the approved direction remains nearly all destructible except immune. External claims and world-protection/admission hooks are unknown, so there is no gameplay entry. Vanilla is deferred.
- Prepare the complete plan before effects; when writes are enabled, revalidate each block immediately before its write and explicitly notify graph/atmosphere integrations as applicable. Do not imply transactional world writes. Damage entities at most once per event. Puddle and speech acceptance must observe the actual updated world, not mocked callbacks.

M1 is implemented/in progress. M2 has focused test-only GameTest evidence, recorded with run details, scope, and limitations in [M2 verification](m2-verification.md). Historical M2 focused runs each had the same required `farplayercannotdespawnboundunboundorcorpse` failure. Current post-M3-migration validation is recorded in [M3](m3-player-grenade.md): 1,752 JUnit tests passed; isolated GameTests had one unrelated required failure, so no whole-suite pass is claimed.

## Remaining gameplay decisions

| Topic | Status / gate |
|---|---|
| Geometry and target semantics | **Approved baseline:** structural floors, ceilings, and walls share a breach rule. The limited fixture whitelist is not permanent target policy; gameplay class mapping remains open. |
| Numeric translation | **Pending evidence and explicit approval.** First establish independent unit-test baseline without tuning; measure representative 3D reach/work against pinned SS14 references. Spherical-shell growth is roughly `r²`, so unsaturated total work is roughly `r⁴` rather than 2D `r³`; numeric reach is unknown. Record every proposed change and its evidence before accepting it. |
| Local work bound | Test fixture uses 128 sampled cells and 32 iterations, rejecting the full plan on cap. Not SS14 parity or gameplay acceptance; per-tick production policy remains open. |
| Destructibility | **Approved direction:** nearly all destructible except exceptional immunity/protection; placement/provenance is not immunity. Fixture whitelist is temporary test scope. External claims/protection hooks and exact gameplay immune set remain unknown, so no gameplay entry. |
| Vanilla | **Approved direction:** prioritize modded blocks. Defer vanilla adapter if it complicates the first implementation; vanilla blast-resistance values are not SS14 parity. |
| Damage | Test prototype contains typed values; gameplay damage mapping, eligibility, and balance remain pending. Use existing `DamageSystem`; no balance approval is implied. |
| Atmosphere / chunk behavior | Atmosphere remains default-off; permitted enabled fixture and topology ownership require a gate. Never load chunks or schedule phantom effects across unloaded boundaries. |
| Trigger | M1 is pure only. Actual trigger/application acceptance belongs in trusted server-only GameTest; no player/public trigger is approved. |

## M1 pure-kernel checks

Implement only a deterministic pure kernel with injected sampler and immutable plan output. No Minecraft state access, chunk loading, mutation, entities, service callbacks, or side effects. Before any tuning, capture a baseline for:

1. Fixed six-neighbor order, symmetry, repeatability, and seeded rolls from event seed plus stable coordinates/roll index.
2. Identical structural breach outcomes for equivalent wall, floor, and ceiling samples; optional decorative cover damage is separately represented and cannot imply structural breach.
3. Open shaft, corners, thin/thick barriers, intensity/falloff/cap, duplicate visits, and unloaded boundary stop behavior.
4. Finite energy/work accounting, no revisits multiplying energy, and explicit saturation/reject result at the proposed event bounds; no partial plan accepted on overflow.
5. A fake sampler test proving the kernel has no world mutation or other effects.

Keep expected values and measured 3D reach/work as artifacts. Do not tune until that independent baseline is reviewed. If evidence motivates numeric changes, document comparison and obtain approval before adopting them.

## Follow-up checks (not owner-approval gates blocking test-only work)

- **M2 test-only:** focused evidence for fixture behaviors is documented in [M2 verification](m2-verification.md); broader safety/performance and gameplay acceptance remain open. No gameplay trigger pending external protection/claims and gameplay acceptance.
- **M3:** real loaded-world integration evidence for power cleanup, atmosphere only when explicitly enabled/permitted, puddle support reaction, and delivery-time speech occlusion. A direct `setBlock` is not proof of lifecycle notifications.
- **M4:** representative performance/fairness review before any production trigger. No VFX, public trigger, shrapnel, impulse, or fire effect is implied.

## Known limitations

Reference numbers and fixture bounds are not calibrated to 3D gameplay. Structural classification beyond the fixture, world-protection/claims hooks, permanent immunity policy, typed damage balance, and permitted atmosphere behavior remain open. No gameplay entry, owner-approved numeric balance, or parity assertion follows from these test-only choices.
