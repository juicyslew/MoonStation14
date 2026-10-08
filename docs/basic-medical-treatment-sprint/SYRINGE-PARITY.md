# Basic syringe contract

Verified upstream revision: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

## Composition and controls

`BaseItem → BaseSyringe → Syringe`: Tiny item, an empty 15-unit injector solution,
initial `SyringeDrawMode`, and allowed draw/inject modes. Default transfer amount
is 5 units; both modes offer 5, 10 and 15 units. Syringe is not a healing item.
In-hand use switches modes; amount selection is a separate alternative verb.
Neither basic mode configures a sound, so none is invented. The full basic
entity parent chain has no UseDelay component, so no cooldown is added.
Successful drawing switches to inject mode when completely full; successful
injection switches to draw mode when completely empty. Partial doses keep mode.
The local implementation stages these transitions from actual post-transfer
contents and resolved capacity in the same held-item transfer commit. It retains
the dose when valid for the target mode, otherwise selects the resolved default
or first valid target-mode amount.

Mode prototypes resolve `BaseDrawMode`/`BaseInjectMode` and `BaseSyringeMode`
inheritance. Mob delay is 5 seconds plus 0.1 seconds per admitted unit above 5.
Self use multiplies by 0.5; otherwise a downed target multiplies by 0.5. These
are priority alternatives, not stacked multipliers. Downed refers to physical
standing state, not exclusively Critical/Dead. Container injection has zero
DoAfter duration; drawing uses only `ContainerDrawTime` (zero for the basic
mode). Neither adds volume delay or self/downed modifiers. Selected 5/10/15 is
clamped by available source and destination free capacity.

Positive configured container-draw DoAfter breaks on movement and damage, requires the initial hand, and breaks
on hand change. Injector movement threshold is 0.1 tile. There is no automatic
repeat. Canceled operations transfer nothing. Critical/Dead targets are not
excluded merely by life state: blood/solution composition determines eligibility.
The current local basic-injector runtime requires an occupied hand and cancels on
hand change. Typed policy variants disabling either constraint are rejected.
Closed-container gating is not modeled because current world reagent containers
have no openable-component runtime; `ignore_closed=false` is rejected rather than
claimed as enforced. `ignore_mobs=true` is also rejected: basic syringes target
bodies, not an alternate non-mob injector path.

## Solutions and effects

The pinned organic mob composition marks its named `bloodstream` solution
injectable. It does **not** provide a separate injectable `chemicals` compartment.
Blood draw uses the actual blood-solution mixture. Injection admits the actual
amount limited by source, selection and destination capacity, dispatches the
Injection reaction on the removed dose, then adds it to the target solution.

Group reactions require both reagent and target to allow Injection for the
matching group. Existing Human/Pig reactive groups do not allow Injection;
they must not be broadened to make Touch effects run on injection. Ordinary
injected medicines still use existing bloodstream metabolism.

Local solution capacity is resolved from bloodstream policy: reference volume
times maximum-volume modifier. Existing Human/Pig reference fills/caps are
300/600 and 150/300 units. This is the port's capacity rule; the audit did not
establish an identical upstream runtime `Solution.MaxVolume`. Reference blood
fraction uses reference constituents, not total solution volume. Blood metadata
and the circulating solution remain distinct. Transfers use exact hundredths,
proportional mixture splitting and the existing owner-specific mutation seams.
Sequential endpoint writes are not crash-atomic transactions.

## Source index

- `Resources/Prototypes/Entities/Objects/Specific/Chemistry/chemistry.yml`.
- `Resources/Prototypes/Chemistry/injector_modes.yml`.
- `Content.Shared/Chemistry/{Components/InjectorComponent.cs,Prototypes/InjectorModePrototype.cs,EntitySystems/InjectorSystem.cs}`.
- `Resources/Prototypes/Entities/Mobs/base.yml:MobBloodstream`.
- `Content.Shared/Body/Components/BloodstreamComponent.cs`.
- `Content.Shared/Chemistry/ReactiveSystem.cs` and
  `Content.Shared/EntityEffects/SharedEntityEffectsSystem.cs:OnReactive`.
- Local bloodstream scheduling: `EntityActivitySystem:reconcile`,
  `TickHooks:ReagentUpdate,runMetabolism`, and `MS14Provider:updateIfChanged`.

Container timing follows pinned `Content.Shared/Chemistry/EntitySystems/InjectorSystem.cs`
(`GetContainerDoAfterTime`): the local zero-tick injection uses existing atomic
`BodyLiquidInteraction.fillDoseOwned` as a Minecraft adaptation, not a zero-time
engine interaction. Container target reach is consistently 2.0 blocks.
The focused native fixture exercises this trusted body-owned transfer consumer
with resolved syringe policy/modes and persistent selected-dose data (dose cents
are read from the held stack), with the nearest non-inflated jug face 1.75 blocks
from the body eye. The transfer plan has no global five-unit ceiling; selected
dose, source availability, and destination free capacity bound the admitted
amount. The fixture does not exercise
`SyringeContainerService` request admission or authenticated connected-player
flow.

The item, persistent mode/dose, native fill/mode art, body/world transfer
consumers and shared hand-input routes are implemented. The owner accepted and
closed the sprint on 2026-10-07; automated authenticated request-flow coverage
remains absent. See `HANDOFF.md` for closure and current boundaries,
`VALIDATION.md` for evidence, and `SYRINGE-MANUAL.md` for retained checks.
