# Slippery produce: shared response dependencies

This is an implementation index/decision, not readiness or owner acceptance. Preserve the existing puddle source/event contract and accepted-movement authority. No alternate physics motor is enabled by this work.

## Verified source

SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. `BaseMob` declares Stamina, inherited by human; absent component means no stamina effect. `SlipperySystem.cs:98–155` raises source SlipEvent, changes victim velocity/conditional sliding, then (if not already down) raises **DropHandItemsEvent on the source**, applies victim stun/friction/stamina/audio, then forces victim knockdown. The event has no target payload; HandsSystem drops the receiver's hands. Do not reinterpret this as victim hand dropping. Typical peels/puddles have no hands.

Peels/Slipify default AffectsSliding=false. The slip path omits SlipData.AutoStand; SharedStunSystem.TryKnockdown's default autoStand=true is consumed instead. Friction status sets both friction and acceleration modifiers for0.5s, not movement speed or permanent base physics.

`SharedStaminaSystem.cs:265–453`, `StaminaComponent.cs`, and resistance handlers establish:
- BeforeStaminaDamageEvent can cancel; resistance changes event value unless ignored; universal multiplier defaults1.
- Add damage, floor at0; positive increase extends recovery deadline to at least now+3s. Existing critical state ignores subsequent deltas after admission/event handling.
- At100 enter critical, clamp to100 and attempt6s paralysis. Recovery is deferred6s+**3s buffer** (`SharedStaminaSystem.cs:53,404`).
- When due, exit critical, then recover15/s (Decay3×AfterCritMultiplier5). Normal recovery is3/s; update cadence1s. Recovery uses the same event/modifier path. AfterCritical clears at0.
- Slowdown uses greatest threshold at/below damage/threshold:0→1,.6→.7,.8→.5. Networked resource/state remains server-authoritative.

Normal recovery to0 updates StaminaLow to neutral factor1 and leaves the status; only after-critical recovery clearing at0 removes it (`SharedStaminaSystem.cs:300–309`). Preserve that distinction rather than applying speculative cleanup. Capability removal locally clears only the resource's own status/state, never unrelated stun.

## Existing local owners and gaps

Reuse character component composition/identity, typed prototype inheritance, synchronized attachment infrastructure, StatusEffectSystem, CharacterControlSystem, PostureSystem and movement projection. Stamina is a separate resource, never a DamageSystem health entry. No universal capability: initially activate only on a verified mapped character composition once its live consumer exists.

SlipSystem currently owns puddle admission/latches and response; ReactiveTouch consumes its puddle events. LooseItemEntity already resolves current stack LooseObjectProfile, a possible home for typed Slippery/StepTrigger only when actual consumers are added. There is no current persistent mutation-record/harvest trait consumer; do not imply genetics integration from profile authoring.

StatusEffectBehavior has no timed friction behavior. SlidingFrictionSystem is a separate contact query, not a timed status. PostureSystem's auto-stand policy must be established through its existing owner, not inferred from status expiry. Resolve friction/acceleration against real movement owners before adding fields. No parallel timer/status store or speed-as-friction substitution.

The current Minecraft mapping is already explicit in `MinecraftSlidingPhysics`: acceleration×factor and horizontal retention=`vanillaRetention^factor`. Reuse it, not a second damping implementation. A timed factor must be read independently of `SlipSystem.isSliding`/puddle contact, then composed through one read-only query at the existing vanilla mixin, GroundedHarnessWorldStep and ghost/lifecycle snapshot owners. Current source slip writes the same scalar to friction and acceleration; support that required scalar rather than speculative independent controls. A typed status-instance payload can carry it through existing duration/replication; definition and payload validation must agree. Preserve the disabled experimental motor and separate speed multipliers. This establishes an integration direction, not proven client prediction or native motion.

Planned friction ownership: extend the existing closed status behavior/payload/schema/codecs, not a new attachment. Query active, eligible resolved status instances on each side without materializing state, multiply their required scalar with the existing puddle-contact factor, and route all four existing physics/snapshot call sites through the shared query. Lifecycle expiration/removal/invalidated definitions then become neutral without a second timer or cached attribute. Use the existing status duration UPDATE semantics and a0.5s application; no SlipSystem response change until the status consumer and its tests exist. Packet delivery and motor-route acceptance require separate native/client evidence.

If a hand-bearing slippery source needs drops, reuse MedicalIncapacitationEffects' bounded refresh-after-success strategy and BodyHandItemTransfer.forceDropOwned; preserve tokens/revisions and publication compensation. Do not copy the loop into a competing drop-all service or drop victim hands.

## Sequence and evidence

### Puddle response adapter correction (2026-10-20)

Pinned puddle composition is an intentional exception to generic `SlipperyComponent` defaults. `Resources/Prototypes/Entities/Effects/puddle.yml:155–164` sets `staminaDamage: 0` and `frictionStatusTime: 0` to avoid applying friction twice. `SharedPuddleSystem.UpdateSlip:228–269` enables `AffectsSliding` for active puddles; its disabled/small-volume branch disables StepTrigger and sliding. Puddle launch/stun/knockdown/super-slippery data derives from `SlipData`; sliding friction is weighted `ReagentData.Friction` (`SharedPuddleSystem.cs:271–320`), not `SlipData.SlipFriction`. Shared response profiles preserve this distinction: puddle adapter passes sliding true, zero stamina and zero timed-status duration; loose-item adapter defaults to stamina25, friction `.5` for `.5s`, sliding false.

1. Stamina typed defaults, immutable state/reducer and focused prototype/math coverage; no MAIN activation yet.
2. Real body resource authority, synchronized state, recovery/events and existing movement/paralysis projection; then activate verified human composition and author native fixtures. No inert counter.
3. Shared timed slip response/contact extension, then persistent mutation/harvest trait composition and authentic peel bindings.

Phase 2 bounded timed-friction consumer checkpoint: `StatusEffectSystem.frictionFactor` reads only an existing attachment and side-resolved status catalog, while `SlidingFrictionSystem.frictionFactor` multiplies it with the existing sliding-gated puddle factor. All four established movement consumers (mixin, GroundedHarnessWorldStep, GhostMobHarnessControl and LifecycleCharacterSessionControl) already call this shared method. `slip_friction` resolves with friction default 0.5; payload overrides it. Tagged live source fixtures cover entity query, independent timed effect, composition, expiry/removal and catalog invalidation/restoration; they are authored but not compiled or executed yet. Client-side catalog delivery and native motion remain unverified. No SlipSystem response application was added in this phase.

Each phase is incomplete feature work until its live consumers are wired. Focused JVM/compile checks precede source review. Native/contact/player-client behavior and whole-feature readiness remain integration-gated; no intermediate readiness submission.
