# Active character capabilities

## Scope

Owner requested player-triggerable abilities granted by character composition,
live state and equipment. First slice: combat/harm-mode toggle, scream, an ability
bar/menu, and equipment action grants/removal. Include an operator-only debug
wearable granting a simple visible pulse so the equipment path can be exercised.
Melee attacks are explicitly a later sprint. Do not reopen animal model work.

## Ordered work

1. Resolve pinned SS14 action/provider composition and map it to supported local
   typed prototypes. Reuse committed-body authority, equipment ownership,
   emote/effect execution and shared UI/input owners.
2. Implement current-body action discovery and authoritative activation, including
   action-specific admission, provider lifecycle, cooldown and toggled-state
   snapshots. Combat mode is body state, not an attack implementation.
3. Present the actions through the shared gameplay UI/input platform, with icons,
    toggle state and cooldown feedback. Integrate the debug wearable through actual
    live hands/equipment, not the parked vanilla player inventory.
    Owner feedback requires a persistent clickable horizontal action bar with
    configurable numeric bindings: combat mode on 1, scream on 2, debug pulse on 3.
    Subsequent manual feedback supersedes the horizontal arrangement: place
    granted buttons vertically down the top-left edge, hide ungranted slots, use
    clear circular cooldown feedback, and retain bar clicks while chat is open.
    The supplementary menu must use shared draggable/non-modal window controls.
4. Validate inherited defaults and live consumers, stale authority/provider
   rejection, state/equipment changes, control transitions, actual effects and UI
   behavior. Use focused cheap checks locally; native/readiness checks belong to
   the serialized coordinator lane.

## Acceptance invariants

- Grants derive from resolved composition and current providers. Never give every
  possessed animal the human action set or make `ComplexInteraction` a universal
  action grant.
- Reuse `BodyActionAuthority` for identity/session resolution and revalidation;
  action-specific permissions remain separate. Equipment transfers remain owned
  by existing live hands/equipment services.
- SS14 combat toggle is a mental action, exempt from interaction and consciousness
  checks. Scream skips interaction checks but requires consciousness and has a
  ten-second action delay. Preserve the distinction within supported body-control
  lifecycle boundaries; do not broaden unrelated action admission.
- Scream uses the normal shared emote/chat/audio effect. Existing emote-command
  behavior is not replaced by the action's cooldown.
  Player-visible body names must use `CharacterPublicName.component`, not native
  entity display names; test registered harness metadata names in the live path.
- Equip/unequip and body/session changes invalidate stale grants and requests.
  Client presentation or pending feedback never authorizes an effect.
- Debug grants are explicitly development-only Minecraft behavior, not invented
  SS14 clothing parity. No speculative targeted-action or future melee framework.
- Preserve attribution for imported SS14 icons/audio. Record unresolved audio
  provenance in `docs/audio-unknown-provenance.md`.

## Reference boundary

Pinned SS14: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
Key references: `Actions/types.yml`, `SharedActionsSystem`, `ActionGrantSystem`,
`SharedCombatModeSystem`, `VocalSystem`, `VocalComponent`, human species parent
composition, and `SprayFlowerPin` worn-item action grants. Source mapping and final
local field/API decisions remain to be recorded after the implementation audit.

This is an active plan, not technical readiness, validation or owner acceptance.
