# Mouse interaction follow-up

## Scope and sequencing

Owner confirmed random wandering, ambient squeaks and drinking after forcing
Parched; reported much less movement jitter. Connected correction behavior has
not been instrumented, so this is owner observation, not a proven jitter cause.

The follow-up selects features the current shared frameworks can reasonably
support:

1. Player-triggered squeak emotes: reuse the existing emote registry/delivery
   implementation and authenticated committed-body speech context. Resolve
   permissions from typed character composition; do not substitute accented
   ordinary speech for an emote.
2. Brief idle interest in nearby players: an explicit Minecraft adaptation,
   subordinate to movement, eating, drinking and player possession. Use existing
   NPC scheduling, facing and loaded visibility services; no competing motor,
   pathfinder or threat framework.
3. Validate composition/inheritance and live consumers, admission and loss of
   control, emote delivery/cadence, and attention yielding to nutrition. Keep
   searches bounded and avoid worsening the previous workload problems.

The selected emote/attention implementation passed scoped source review and final
automated checks: 838 native tests, 3,290 JVM tests and 51 readiness checks. Emote
success uses a canonical mouse-owner lifecycle fixture; attention uses a
server-side FakePlayer observer. Connected visual review remains pending.
Implementation mapping and evidence: `EMOTES-ATTENTION.md`, `VALIDATION.md`.

## Deferred foundation

Living pickup/carrying and inventory escape are not supported by current hand
and storage representations. `LiveHands` and storage own item stacks;
`BodyHandItemTransfer` copies a world item's stack and discards the source entity.
Using that transfer for a mouse would lose living-body semantics.

`BodyContainmentService` preserves exact living-entity identity, but does not
provide hand/inventory occupancy, carried-body ownership, rendering, control or
ghost-role integration. A later living-body carrying feature must establish
those contracts before inventory escape can consume them. Neither placeholder
mouse stacks nor pulling disguised as carrying are acceptable substitutes.

Reactive fleeing is not selected without an established threat model and a
current shared owner; ordinary proximity must not repeatedly interrupt meals.
Headwear, butchering, faxing, combat and a broad emote UI are outside this unit.

No commits, pushes or sprint closure are authorized. This is not a handoff.
