# Item examination, action presentation and vocal audio

## Agreed scope

Owner feedback after accepted wearable integration identified three work units. These remain in development; previous EVA readiness results do not validate this revision. Carrier isolation remains deferred.

1. **Vocal audio:** screams and vocal emotes follow their emitting character for the owner and other listeners. Use the shared entity-attached sound route and native entity sound packets, retaining camera-based audience selection. Fixed-position effects remain positional.
2. **Action presentation:** preserve exact SS14 mask icons and toggle meaning, but remove the extra green pressed treatment when an alternate toggle icon already expresses state. Correct capability tooltip coordinates through the shared layout/render owners.
3. **Item examination:** Shift-primary examines the item under an owned hand, equipment/pocket, or accessible storage cell, without invoking a transfer. Floor items and inventory items share item contributors and authoritative, current data.

## Acceptance

- **AUDIO-01:** accepted normal scream dispatch sends one entity-attached cue identifying the character body, not its transport carrier. Both owner and nearby observer receive the appropriate cue; existing camera/dimension/range audience rules remain. Native client playback follows that entity. No duplicate positional cue is emitted.
- **ACTION-UI-01:** active actions with `iconOn` use that icon and the normal background; active actions without it retain the appropriate pressed treatment. Hover is independent. HUD and window tooltips use absolute GUI coordinates after pose restoration and the existing viewport-clamping renderer.
- **ITEM-EXAMINE-01:** exact Shift-primary on a visible item in hands, clothing/pockets, or accessible storage opens the existing examination popup for that item. Non-examine input, active drag ownership, popup controls and other UI capture remain correct.
- HUD coverage includes both hand tiles and the back/belt shortcut tiles, as well as occupied clothing-panel slots and storage cells. Routing is item-type agnostic; specialized information still comes from the item's applicable contributors.
- Inventory targets are non-spatial: neither `ITEM` nor `STORED_ITEM` may enter world bounds or outline lookup. Opening their popup must not dereference a block position or query world geometry. Popup controls consume a click once before any underlying HUD/world route.
- **ITEM-EXAMINE-02:** the server authenticates body/epoch and resolves a fresh copied stack from its canonical owner. Direct slots, owned storage paths and open world-storage sessions preserve their existing token/revision/session/access checks. Forged, stale, replaced or inaccessible targets fail closed. Same-item refresh reads current contents rather than cached tooltip text.
- **ITEM-EXAMINE-03:** common item contributors serve floor and authorized inventory targets. Gas tanks show SS14's rounded pressure in detail range, connection state and release-valve state. Do not invent gas composition, remaining-time or temperature examination fields. Existing sealed tanks truthfully report a closed valve; this does not implement vent controls.

## Established SS14 correspondence

Pinned source: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

- `ActionToggleMask` uses `Clothing/Mask/gas.rsi`, state `icon`, when raised; `Interface/Default/blocked.png` is `iconOn`, selected when lowered. `ActionButton.UpdateIcons/DrawModeChanged` retains the normal button pseudo-class for a toggled action with an alternate icon. Local matching sprite bytes remain unchanged.
- Inventory, hands and storage UI handlers invoke the examine input function on the contained item UID. The local Shift-primary gesture is retained; inventory examination must not be implemented as a world-ray query behind the UI.
- `SharedGasTankSystem.OnExamined` gates pressure on detail range, but not connection/valve text. It rounds pressure before `PRESSURE`, whose `F1` output scales through kPa, MPa, GPa, TPa and PBa. Pressure is orange.
- `SharedChatSystem.Emote.TryPlayEmoteSound` calls entity-anchored `PlayPvs(sound, uid, parameters)`.

## Validation boundaries

Use focused production-policy/codec/contributor tests and registered-player native fixtures for authenticated item resolution and vocal packet dispatch. Neither a native packet assertion nor a CPU layout test establishes audible playback, GPU tooltip placement or physical mouse interaction. Report those owner observations separately. Import and preserve the accepted EVA wearable family without artwork changes.

## Internals icon and clothing-audio follow-up

- **INTERNALS-ICON-01:** the internals action uses SS14 `internal2` when off and `internal1` when connected, including low-pressure/empty connected tanks. Remove the local low-pressure action override. The two-frame `internal0` is a separate SS14 alert state, not an action-button icon; this correction does not add a new alert system.
- **EQUIPMENT-AUDIO-01:** consume resolved clothing sound fields only after successful worn-equipment changes. EVA helmet equip and unequip use `/Audio/Mecha/mechmove03.ogg`; grey jumpsuit equip uses `/Audio/Items/jumpsuit_equip.ogg`, with silent removal. EVA suit and breath mask have no configured equip/removal sounds. Raising/lowering the mask is silent. Denied, canceled, rolled-back, same-token component-only and non-worn-slot changes emit no clothing cue. A committed replacement compares exact occupant tokens, not item type alone.
- **INTERNALS-AUDIO-01:** a successful tank connection emits the existing `/Audio/Effects/internals.ogg` one-shot at the source default +5 dB. Default disconnect is silent; failed operations emit nothing. There is no continuous flow/breathing loop while internals remain connected.

Source references: `Actions/internals.yml`, `Alerts/alerts.yml`, `Clothing/Head/base_clothinghead.yml:ClothingHeadEVAHelmetBase`, `Clothing/Uniforms/base_clothinguniforms.yml:UnsensoredClothingUniformBase`, `Content.Shared/Clothing/Components/ClothingComponent.cs`, `Content.Shared/Inventory/InventorySystem.Equip.cs`, and `Content.Shared/Atmos/Components/GasTankComponent.cs`, all at the pinned revision above. Source-null sound fields remain silent rather than acquiring invented generic rustle audio.
