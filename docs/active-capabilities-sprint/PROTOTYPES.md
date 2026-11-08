# Active action composition

## Pinned source

Verified SS14 revision: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

- `Resources/Prototypes/Body/Species/human.yml` and `Body/species_base.yml`:
  `MobHuman` inherits `AppearanceHuman` and `BaseSpeciesMobOrganic`; the latter
  inherits `BaseSpeciesMob`, which composes `MobCombat` and adds `CombatMode`,
  `Speech`, `Vocal` and `Emoting`.
- `Entities/Mobs/NPCs/simplemob.yml` and `Entities/Mobs/base.yml`:
  `SimpleMobBase` → `SimpleSpaceMobBase` → `MobCombat` supplies combat mode.
  Local `supported_simple_mob` represents the supported shared subset and grants
  typed `CombatMode`; child animals consume inherited defaults.
- `Entities/Mobs/NPCs/animals.yml` and `scurret.yml`: bat, bee and monkey do not
  inherit `Vocal`. Kobold and scurret explicitly compose it. Speech sounds alone
  are not a vocal-action grant. See the earlier animal ancestry index at
  `docs/nonhostile-animals-sprint/PROTOTYPES.md`.

## Action family and consumers

Local `character_action` resources use the existing `PrototypeResolver` and typed
`PrototypeManager`; abstract parents are excluded from the published catalog.
Canonical directory:
`src/main/resources/data/moonstation14/moonstation14/character_action/`.

| Local ID | Upstream correspondence | Resolved policy |
| --- | --- | --- |
| `combat_mode_toggle` | `ActionCombatModeToggle` → `BaseMentalAction` → `BaseAction` | Toggle; priority -100; no interaction or consciousness check; harm-off/harm icons |
| `scream` | `ActionScream` → `BaseAction` | Instant; interaction check off, consciousness check on; 200-tick delay; scream icon |
| `debug_pulse` | Development-only Minecraft adaptation | Instant; ordinary checks; 20-tick delay; harmless visible particles/feedback |

Source: `Resources/Prototypes/Actions/types.yml`,
`Content.Shared/Actions/Components/ActionComponent.cs`, `SharedActionsSystem`,
`SharedCombatModeSystem`, `VocalSystem` and `VocalComponent`.

`CombatModeComponent` and `VocalComponent` reference resolved action prototypes.
`BodyActionDiscovery` derives grants from current body composition and actual
`LiveHands` equipment providers. `BodyActionService` revalidates committed-body
authority, identity, provider, revision, admission and request sequence before
effects. Server body attachments own combat state and tick-clock cooldowns;
snapshots are presentation, not permission. Consciousness currently maps to the
supported medical ALIVE state, separately from interaction/stun admission.
Native-dead/corpse control is not broadened by the mental-action exemption.

Melee, disarm execution and NPC ability execution are not implemented by this
slice. A combat toggle must not be mistaken for an attack path.

## Worn providers

Upstream `ActionGrantSystem` and `SprayFlowerPin` in `Clothing/Neck/pins.yml`
establish provider-owned actions with `ItemActionGrant.activeIfWorn: true` and
pocket filtering. Local `EquipmentItemData.supportedActionGrant` represents the
supported grant pair. It is consumed from real equipped slot/occupancy tokens;
removal or replacement invalidates stale action requests. This is not permission
to derive grants from the parked player inventory or an item name.

`debug_capability_cap` inherits the existing grey-cap equipment/appearance and
footprint profiles, granting only `debug_pulse` while worn. It is supplied through
the operator command and existing body-give owner, then equipped normally. It is
not an upstream item or a new gameplay clothing ability. Arbitrary nested-inventory
item actions remain outside this first worn-provider slice.

## Vocal effect and assets

Scream activation reuses `EmoteEffect` planning/delivery; its action cooldown is
separate from the existing `/emote` command's admission/rate limit. Vocal audio
uses the existing camera-aware `ControlledSoundDispatch`, with category VOICE,
so listener selection follows the controlled camera rather than carrier position.
Old controlled-sound callers retain their PLAYERS default.

Pinned mappings from `Voice/speech_emote_sounds.yml`, sound collections and audio:

- Human unsexed fallback: MaleHuman → MaleScreams (six existing local recordings).
- Kobold unsexed fallback: MaleReptilian → direct reptilian scream recording.
- Scurret: WawaScream → `Animals/wawa_protest.ogg`.
- Pitch variation: 0.125. Wilhelm chance: 0.0002 normally, 0.01 for scurret.

These are resolved Vocal settings, not species branches in the action dispatcher.
There is no local humanoid sex source: unsexed selection is explicit, not a claim
of sex-dependent voice parity. Existing generic emote-command/reagent behavior is
retained. Chat delivery retains the current emote service's chunk-tracking route;
this does not claim complete upstream emote/acoustic or muting-system parity.

Icons: `docs/actions-provenance.md`. Audio:
`docs/audio/audio-interaction-provenance-notes.md`; unresolved reptilian licensing
is recorded in `docs/audio-unknown-provenance.md`.

## Presentation boundaries

Owner-requested revision adds a persistent vertical top-left clickable bar and
configurable slot bindings: combat mode 1, scream 2, debug pulse 3. Missing grants
omit their buttons rather than moving another action onto that binding.
This fixed default assignment is owner policy, not a claim that upstream always
assigns those action identities to those slots. K remains a supplementary menu.
Pinned upstream references: `Content.Client/UserInterface/Systems/Actions/Widgets/ActionsBar.xaml`
uses a single row; `DefaultGameScreen.xaml` and its code-behind anchor the host
at the top-left. The owner's vertical arrangement deliberately overrides that
upstream orientation. `ActionUIController.cs:117-139,234-247` routes numbered bindings
to slot indices; `Resources/keybinds.yml:554-579` defines numeric defaults.
`Content.Client/Cooldown/CooldownGraphic.cs:24-63` and
`Resources/Textures/Shaders/cooldown.swsl` define a circular cooldown wipe and
completion blink. The port adapts this to GUI rendering; cooldown authority
remains server-derived and independent of toggle/disabled styling.

Chat must retain the visible bar and pointer clicks without admitting typed
numeric keys as action input. The actions menu must use shared window chrome,
geometry, pointer ownership and movement rather than a modal screen replacement.

All shared emote chat naming uses `CharacterPublicName.component(Entity)`.
`Entity.getDisplayName()` alone bypasses this policy for bound harnesses.
Examine and portable-body appearance already consume this same public-name owner.
The prototype metadata name (`Human`) is only a fallback, not the personal name.
The existing durable chat identity ledger supplies generated personal names keyed
by account/profile, already used by normal speech. The shared public-name policy
must reuse that identity without allocating names during display; native custom
name precedence and unnamed/NPC fallbacks remain explicit.

Pinned SS14 emotes use `Identity.Entity(source)` and its current name
(`Content.Server/Chat/Systems/ChatSystem.cs:532-560`), normally the character's
personal name. Full SS14 identity-obscuring/ID-card behavior is outside this fix.
Existing direct disposal climbing defaults to Ctrl+K to avoid its previous K
collision; saved owner keybinds are not overwritten. Menu movement uses the shared
native movement sampler, also consumed by airlock wires. Cooldown, toggle and
disabled feedback are server-derived. Full SS14 drag/reorder, user reassignment
of actions between slots, pages and search are not delivered in this small first
action set. Current revision validation is pending.

The owner-requested compact presentation uses local half-scale paint/input
coordinates, not global GUI settings. Artwork fills 24 of each 28 logical button
pixels (12 of 14 displayed pixels); the approved compact menu is unchanged.
Cooldown presentation anchors every accepted authoritative snapshot to its local
receipt clock, with a per-body/epoch monotonic high-water mark. Elapsed ticks from
before a new anchor must never be added again. Render and input gating share this
clock; server cooldown/admission remain authoritative.

Action-number queues must be discarded during F3 chords without consuming native
hotbar/debug input or replaying actions after F3 release. Vanilla F3+L profiling
requires cursor-locked play (`Minecraft.screen == null`); the free-cursor workspace
is a Screen host and does not qualify for that native debug-key path.
In the mapped NeoForge 21.1.224 client, `KeyboardHandler.keyPress` calls
`KeyMapping.click` before `ClientHooks.onKeyInput`, which posts `InputEvent.Key`.
The action guard therefore discards an already-recorded mod click at event time;
it does not cancel the native event or depend only on F3 still being held next tick.

This mapping is not a test receipt or owner acceptance. See current invocation
evidence for validation; physical input, pixels and subjective usability require
their own review.
