# Mk58 owner feedback — correction scope

The previous technical PASS (`run_4b7d9acb0beb24aab152cdbc`) was not owner
acceptance. Fix this playable path before asking for the other weapons to be tested.

| Owner report | Required correction/evidence |
|---|---|
| Repeated racking loses sound | Each accepted rack has the appropriate manipulation cue; exercise consecutive requests, not only sound selection. |
| Aiming arm points backward | Correct the actual humanoid/native-item coordinate chain for both handings, without changing artist geometry or display poses. |
| Ammo counter under the other hand | Anchor to the presented source hand's actual HUD tile; verify both active hands. |
| Casings lack textures | Resolve real packaged sprite/model paths, including spent states. |
| Casings should pop out, bounce/roll and sound | Verify upstream composition/assets; implement the requested transient spent-casing presentation through shared visual/audio owners. Recoverable live rounds remain real items. |
| Empty-weapon text | Remove routine empty-fire text; retain the empty sound and exceptional diagnostics. |
| Magazine fullness unclear | Give the pistol magazine an unambiguous current/capacity presentation driven by its real contents. |
| Held reload click/drag fails | Establish SS14 click precedence; active ammunition must be usable on the other hand's compatible gun/container before changing hand selection. Preserve drag loading too. |
| Gun menu violates shared UI contract | Use existing small, draggable, closable gameplay-window ownership, including operation with cursor capture. |
| Replacement magazine still cannot fire | Exercise actual acquisition → insertion/refill → chambering → firing/depletion through public client/server entry paths. No fixture-only state injection or ground-placement workaround. |

Preserve real item-owned ammunition/charge, atomic transfers, wield reservations,
prediction/authority separation, the eight-gun scope, and owner-deferred structural
health. Cover live consumers and the shared boundaries implicated by these reports;
do not equate old helper-test passes with reproduction or visual acceptance.
