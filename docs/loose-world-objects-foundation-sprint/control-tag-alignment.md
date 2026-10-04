# Ventilation controls and fixture-tag integration

Follow-up integration of local `main` (`cac08d0`) with the accepted foundation
(`e543f49`). The foundation handoff remains the architectural starting point.

## Controls

- Left-click uses/installs held supplies; an aimed loose object takes priority
  over a block behind it (empty-hand exact pickup, occupied-hand target use).
- E opens aimed storage or activates the world block, including air alarms/APCs
  while holding supplies. It does not install supplies or prime grenades.
- Alt+E/Alt+left-click share alternate liquid/puddle use. Shift+E and Shift+B
  retain belt/backpack smart equip/store; V/Shift+V retain storage windows.
- Z is held activation; grenade primary use is separate. Ctrl+Q retains generic
  whole-stack loose throwing. Successful block-target grenade priming reports
  `HAND_CHANGED`, invalidating hand proof and requesting a fresh snapshot.
- Pending block requests retain HUD presentation but disable mutations until
  completion or refreshed authority. Keyboard world gestures remain tick-owned.

## Fixtures

Foundation fixtures use `@GameTestTags`, explicit `moonstation14` template
namespace and the shared registrar, not automatic `@GameTestHolder` discovery.
`loose-objects` selects the foundation-specific fixtures; overlapping subsystem
tags remain available. Multiple requested tags use OR, not intersection.

Tags do not isolate worlds or nearby fixtures. Throw assertions compare newly
spawned entity UUIDs against a before-set; sealed-host metadata assertions
compare the unchanged block state rather than assuming an external cell is air.
Use fresh run directories when fixtures assume empty dimension SavedData.

## Manual check

1. With an active body holding supplies: left-click installs; E opens an alarm/APC.
2. Aim at a loose object over a floor: left-click targets that object, not the floor.
3. Check Alt liquid use, Shift belt/backpack storage and grenade left-click/throw;
   E and Z must not prime a grenade. UI clicks must not act on the world.

Restart both client and server before connected testing; the merged packet
action/result enums must match. Connected testing of this integration is not
yet recorded. Automated validation evidence follows.

## Automated validation

- Unit suite: **2,102 passed**, zero failures/errors/skips
  (`build/controls-tags-unit-final.log`). Production and fixture compilation passed.
- Clean filtered `loose-objects`: **18/502 selected, all 18 passed**
  (`build/controls-tags-loose-clean.log`). Successful disposable world removed.
- Clean filtered `loose-objects,ventilation,air-alarm`: **73/502 selected,
  72 passed, 1 failed** (`build/controls-tags-focused-clean.log`). The remaining
  `sealedsegmentadmissionisclickedonlyandquarantinesreplacement` assertion expects
  one clicked pipe record; its failure world is retained. No loose-object test failed.
- Full GameTests: **502/502 selected, 498 passed, 4 failed**, zero untagged.
  Failures are `poweredventmetertracksbreakerandreplacement`,
  `disableddevicedoesnotmovegas`, `poweredscrubbercapturesonlyselectedgas` and
  `onlycompletedpaidsolvemovesconservedgas` (unknown power meters/paid solves).
  This is not a green full suite (`build/controls-tags-full.log`); failed world
  retained in `build/controls-tags-full-fixture`.
  Production power/ventilation runtimes match local `main`; these failures are
  not established as baseline without a matching `main` run.
- Earlier filtered attempts exposed adjacent-fixture assumptions, repaired as
  above, then stale SavedData assumptions when reusing the failed fixture world.
  Their logs/world remain as diagnostics, not final acceptance evidence.
- Input/backend/tagging source reviews passed. Connected mouse/menu operation
  remains a manual check. Local `main` was merged with conflicts resolved;
  the merge and follow-up edits remain uncommitted.

When committing, include the new alignment document and
`NormalWorldActivationGestureTest.java` alongside the merge and tracked edits.
