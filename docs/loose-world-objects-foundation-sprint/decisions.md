# Foundation decisions and parity references

Implementation was authorized October 3, 2026. These are architectural decisions, not a claim of completed validation or owner-connected acceptance.

## Pinned references

- SS14: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
- RobustToolbox: `3136118b5338ef2d9580178caf5c723e65eb76e7`.
- Port starting revision: `87b2aa75f8d4763311de2d121bcf8b7f42cee6b5`.

SS14 `Resources/Prototypes/Entities/Objects/base_item.yml` combines persistent item entities, clickable/pullable physics, gravity and collision wake. Base fixture restitution is 0.3 and friction 0.2. Physics sleeping does not mean entity deletion or zero server cost.

The server `HandsSystem` throw caller, not just `ThrowingSystem.TryThrow`, supplies the relevant defaults: speed 11, target distance 0.1–8, cooldown 0.5 seconds. `Resources/keybinds.yml` binds throwing to Ctrl+Q. The port's 20 ticks/second makes the nominal speed 0.55 blocks/tick, cooldown 10 ticks. Three-dimensional gravity/contact response is an adaptation, not numeric equivalence to SS14's top-down physics.

SS14 pulling preserves the target entity. `PullingSystem` uses a non-colliding distance joint, minimum 0 and maximum initial center distance + 0.15. `NeedsHands=true` creates a separate virtual hand item; it does not transfer the target into a hand. `PullerComponent` applies a 0.95 movement multiplier. Incapacitation, container insertion and lifecycle changes terminate pulling.

## Local choices

### World host and ownership

A separately registered `LooseItemEntity` subclasses Minecraft's stack-host `ItemEntity` API. This deliberately retains existing typed transfer/storage-session seams. Its renderer and tick/lifetime/pickup rules are its own; it is not registered as vanilla `EntityType.ITEM` and does not replace vanilla items globally. This is a Minecraft integration choice, not SS14 class inheritance parity.

Eligible controlled-body drop/throw routes create loose entities. Other vanilla and Creative routes retain their existing representation and behavior. One stack owns all item components. No walk-over pickup, automatic merging or vanilla age despawn applies to the new entity. Whole-stack transfer remains the contract. Normal entity NBT persists contents, identity and orientation. Settling and pulling do not change representation or identity.

**Connected-feedback amendment:** the owner requested mod-item Creative drops to use loose objects and asked how to retrieve them. Actual server Creative drop interception records the authentic dropper before spawning; only MoonStation14 item drops are converted. Uncontrolled Creative players may retrieve loose items through ordinary inventory pickup after a short delay. Controlled-body carriers cannot bypass body-hand ownership through that route. Unrelated vanilla Creative drops remain unchanged. This supersedes the original Creative boundary for mod items only.

### Data and physics

Loose-object profiles use the existing prototype manager, JSON inheritance and server/client catalogs. Item components nominate a profile; ordinary held items use the generic profile. Jug appearance/impact breakage are profile-driven. Grenade deadline/beep state remains its existing item component, evaluated regardless of whether the world object is moving.

Server simulation owns motion and contact effects; clients consume tracked transforms/state rather than predicting ownership or physical effects. A bounded velocity/impulse pull solver stands in for Robust's distance joint. Do not describe it as a native Minecraft joint or teleport-follow. Chunk prechecks bound motion; loaded-only shape queries do not prove arbitrary callbacks cannot load chunks.

### Liquids and physiology

The existing project jug is glass, whereas upstream `Pitcher` in `drinks_special.yml` is metal and holds 60 units. Retain the local jug's capacity/model and glass break/spill behavior; do not claim that breakage is SS14 metal-pitcher parity. Local five-unit doses and a 20-tick sipping interval are preserved. Liquid plans use integer-cent quantities and actual admitted amounts.

The port currently has prototype stomach eligibility and a body organ model focused on lungs, not removable mouth/stomach organs. Add an explicit mouth capability to eligible character prototypes and require initialized body plus existing stomach eligibility. This is a bounded physiology seam—not an invented full organ simulation. Physical removal of mouth/stomach remains unsupported and must be reported as such. Controlled-body ingestion must not invoke vanilla carrier `JugItem.use` or rely on the old unguarded paired-write ingestion path.

### Targeting, reservations and input

Exact nominated pile pickup is distinct from the retained nearest-hint pickup route used by existing fixtures. Candidates are bounded, deterministic and UUID-stable; server reach, occlusion, authority and current-owner checks remain decisive. Input over storage screens must never fall through to world actions.

Pull reservations are explicit hand metadata, not held item stacks. They block real item entry, publish revisions and identify the exact world target. They are transient and omitted from saves; a restart must not leave a hand reserved for a nonexistent pull. Other hands remain usable.

## Judgement and evidence boundaries

The main adaptations for owner judgement are the registered stack-host subclass, bounded pull solver, explicit prototype mouth capability, three-dimensional throw tuning and pile-selection controls. These decisions avoid prematurely introducing a general rigid-body engine or an unrelated organ redesign.

The pull solver leaves the captured radius slack and applies bounded **inward** radial correction only beyond it. A 1.5-block solver-lag margin, additionally capped by interaction reach, accommodates acceleration before invalidating the pull. World collision remains authoritative; this is not a perfectly rigid or client-predicted rope.

The puller's movement snapshot carries an authoritative pulling flag. Client controlled-body prediction and server voluntary movement both apply the shared 0.95 factor, alongside knockdown scaling. The loose target's motion itself remains server-simulated.

Ctrl+Q is the SS14 throw default. P and Ctrl+left-click toggle pulling; ] cycles the bounded pile. E retains storage opening precedence and otherwise uses the held liquid container on a target; Shift+E reverses transfer or sips a loose jug with an empty hand. Z uses the held item. Legacy N/M enter the same prime/throw services. A same-frame use-then-throw waits for success and fresh proof, including the generic Z path.

**Connected-feedback amendment:** Shift+E has its own binding and the conflicting default belt shortcut is unbound. Pile text is replaced by a local in-world physical-box outline. Generic sprites lie flat; measured bag/steel/glass profiles target a 0.7-block longest opaque dimension. Fixed throw speed is 0.75 blocks/tick at the owner's request for stronger throwing, rather than the original 0.55 reference conversion. Charged throwing and render-distance changes remain out of scope.

SS14's `Drink` edible prototype selects `Audio/Items/drink.ogg` at −2 dB with pitch variation 0.25. The copied file is recorded in `docs/audio-unknown-provenance.md`: individual author/license remain unresolved. No permission or author is inferred from its presence upstream.

Automated fixtures are not authenticated two-client acceptance. Software-rendered model diagnostics are not Minecraft viewport captures. Direct entity-tick benchmarks are not total-server tick, network, frame-time or player-capacity measurements. Report each category separately and retain unresolved limits rather than inventing completion evidence.

Stored armed grenades remain a known parity gap inherited from the incoming grenade subsystem: direct held and loose-root stacks advance, but bag child stacks are not independently ticked. SS14 `TriggerSystem.Timer.UpdateTimer` evaluates contained entities too. This sprint establishes root-owner throw/fuse continuity, not a general contained-item timer traversal or offline expiry guarantee. Do not describe paused child processing as intentional SS14 behavior.
