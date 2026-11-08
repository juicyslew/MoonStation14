# Particle accelerator profile mapping

The pinned SS14 revision is `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. Its PA chain in `Resources/Prototypes/Entities/Structures/Power/Generation/PA/` is `ParticleAcceleratorBase` → `ParticleAcceleratorFinishedPart` → each finished ControlBox/PowerBox/FuelChamber/EndCap/Emitter; unfinished variants instead inherit `ParticleAcceleratorUnfinishedBase` → Base. Base supplies anchored static physics, rotatable transform, fixtures, appearance and interaction. FinishedPart supplies completed visuals/construction node. ControlBox adds APC 250, MultipartMachine part definitions (offsets and -90 rotation), and UI; PowerBox adds MV PowerConsumer; Fuel/End/Emitters carry their respective component identities and completed construction nodes. Runtime multipart assembly consumes the part roles, expected completed nodes, offsets and orientations; the control system fires only after assembled and charged. Sources: `base_particleaccelerator.yml`, `control_box.yml`, `power_box.yml`, `fuel_chamber.yml`, `end_cap.yml`, `emitter.yml`; system `Content.Server/ParticleAccelerator/EntitySystems/ParticleAcceleratorSystem.ControlBox.cs`.

Local typed profile preserves coordinates/role/prototype/orientation as data, exposes an identity contract for future block entities, and rejects non-identity snapshots rather than treating arbitrary block entities as parts. RobustToolbox `Robust.Shared.Maths/Angle.cs` defines `ToWorldVec()` as `(this - PiOver2).ToVec()`: angle 0 is south `(0,-1)` and -90° is west `(-1,0)` in source XY. The explicit Minecraft projection is `(x,y) → (X=x,Y=0,Z=-y)`, flipping source Y because Minecraft south is positive Z. This is a coordinate-system adaptation, not a PA gameplay change. With control facing south, default end is `(1,0,1)` and emitter positions are `(-2,0,2)`, `(-2,0,1)`, `(-2,0,0)`; they fire westward in three Z-separated lanes. The relative source angle -90° maps to Minecraft `CLOCKWISE_90` (SOUTH→WEST); +90° maps to `COUNTERCLOCKWISE_90`. Resource prototypes resolve through the existing parent resolver; abstract `pa_base` supplies defaults to the concrete profile.

Strength semantics follow `Content.Shared/Singularity/Components/SharedParticleAcceleratorComponent.cs`: SS14 state values are Standby=1, Level0=2, Level1=3, Level2=4, Level3=5 (not ordinal ranks). `Content.Server/ParticleAccelerator/EntitySystems/ParticleAcceleratorSystem.Emitter.cs` sets singularity food by selected strength on every emitter (factor 1 default): 0, 10, 20, 30, 60. Energy / generator-charge lookup remains strength-level data, never part data. The local strength table also records power box MV draws 500/2000/3500/5000/6500 and explicit default STANDBY, maximum LEVEL_2. APC control draw 250 remains a separately typed field.

Runtime mapping update: seven registered native-placeholder blocks now carry typed PA identities. The control BE stores the resolved profile root (`moonstation14:particle_accelerator`); each physical part's identity is validated against that profile's leaf `completed_prototype`. Assembly uses the profile footprint and loaded-only block-entity snapshots. The control draws the profile's `control_power_ap` on APC; only the POWER role exposes MV demand, from the selected resolved strength row (or the 500 W STANDBY row while disabled). Both current generic power meters must be fresh and meet the source `RequiredPowerRatio` (0.999) before `firingEligible`; standby is never firing-eligible.

Verified upstream behavior at pinned revision `c9df5ef5d675b0d1d226828bddf6b78c28502d91`: `Content.Server/ParticleAccelerator/EntitySystems/ParticleAcceleratorSystem.ControlBox.cs` lines 90-94 switches on when the power-box draw is met at `RequiredPowerRatio`; lines 109-112 resets strength to Standby on switch-off; lines 232-244 sets power-box draw to `BasePowerDraw` while disabled and adds `LevelPowerDraw * selected strength` only while enabled; lines 210-217 excludes Standby from firing. `Content.Server/ParticleAccelerator/EntitySystems/ParticleAcceleratorSystem.PowerBox.cs` lines 15-28 powers the control only while enabled and received power meets the same ratio. `Content.Server/ParticleAccelerator/Components/ParticleAcceleratorControlBoxComponent.cs` lines 56, 63, 70 define 0.999, 500 W base, and 1500 W per level. Thus the baseline 500 W request while off is deliberate parity. APC and MV remain separate local network tiers; unsupported SS14 components are not introduced.

## Wave and operator contract (verified; producer/UI pending)

`UpdateFiring` overwrites the initially zero timer when eligibility becomes true:
`LastFire = now`, `NextFire = now + ChargeTime`. Default charge time is six
seconds. The **first wave is delayed six seconds**, not immediate. Each wave
emits one particle from each of Port, Fore and Starboard simultaneously; losing
eligibility stops firing and regaining it starts a fresh charge interval.
Standby never fires; switching off restores Standby.

Upstream UI state includes assembly, enabled/strength, actual power-box draw and
received power, part presence, interface availability, maximum strength and
strength lock. It does not expose powered/firing flags or charge countdown.
Server handlers enforce control APC availability, assembly for enable and the
strength/interface restrictions; rescan is available without complete assembly.
Ordinary maximum is Level 2; Level 3 requires the limiter-wire capability.
Do not invent unsupported local wire components or replace these controls with
a Minecraft sneak-cycle shortcut. Sources: control-box system `UpdateFiring`,
shared `ParticleAcceleratorUIState` and client control-menu `UpdateUI`.

### Access presentation versus handler authority

The control prototype declares `AccessReader` with Engineering access. The
client menu computes access for its attached entity through `AccessReaderSystem`;
it disables enable, rescan and strength widgets when that check fails. Access is
not a field in the UI state. Server PA message handlers check interface/APC
availability but do **not** repeat the access check. Preserve this distinction;
do not claim an Engineering-only server lock as upstream parity. Local body-hand
authority, target identity and freshness checks remain required independently.
Sources: control-menu `UpdateUI:210–225`, shared UI state `:50–86`, control-box
state construction `:247–286` and request handlers `:376–426` at the pinned revision.

### APC overload and PA bootstrap status

At pinned SS14, `apc.yml` resolves `APCBasic -> BaseAPC -> BaseWallmount`;
`ApcComponent` defaults are 20,000 W and 3 s. `ApcSystem.Update` trips only
when current supply is strictly above the limit for elapsed time greater than
3 s; reclosing clears the trip flag. Local 20 kW/3 s targets match, but cached
solve-age (20 ticks) and topology-identity freshness/reset gates are Minecraft
admission adaptations, not upstream timing parity. The reported 22-sample
counter at 29,800 W without a trip does not establish 61 consecutive observations;
cause is unresolved, not harmless. Separately, known 0 W bootstrap MV delivery is
a real shortfall: disabled PA still requests its resolved 500 W STANDBY draw, in
addition to the 250 W APC receiver. Diagnosis needs per-observation tick/age,
known state, topology generation and counter transitions plus source/substation
MV allocation trace. No trip-policy or deadline change is authorized.
