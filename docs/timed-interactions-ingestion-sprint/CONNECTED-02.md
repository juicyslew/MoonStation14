# Connected feedback 02

Reported: Alt+left-click puddle drinking works, Alt+E does not; puddle sipping is
unexpectedly slow; drinking blood appears to cause damage.

## Input correction

Alt+E previously depended on NeoForge's modifier-specific mapping being consumed.
The working mouse gesture directly entered `alternateWorldUse`. The keyboard now
also captures Alt at a press of the rebindable ordinary-world-use key, and sends
that intent through the same alternate helper. Modified and ordinary mapping
signals coalesce to one gesture; ordinary E and Shift+E retain their routes.
In-game keyboard confirmation remains necessary.

## Verified behavior

- Pinned SS14 `Resources/Prototypes/Entities/Effects/puddle.yml:204–209` explicitly
  sets Edible delay=3 and transferAmount=1. This overrides the usual five-unit dose.
  Current prototype rates are unchanged: one unit per three seconds from puddles,
  five units per second from held drinks.
- Blood digestion produces `UncookedAnimalProteins` at .2 per unit metabolized.
  Its bloodstream effects include poison damage for Human (inverted Animal/Vox/Plant
  condition), plus probabilistic sickness/vomiting. This downstream product can
  harm a human who swallows blood and matches SS14. It is not Blood's separate
  bloodstream-stage poison effect: raw Blood has Digestion metabolism and is
  excluded from the non-digestion stomach-to-body transfer.
- Sources: pinned `Resources/Prototypes/Reagents/{biological,toxins}.yml`, shared
  `MetabolizerSystem`/`MetabolizerComponent`; local `reagent/{blood,uncookedanimalproteins}.json`,
  `StomachDigestionTransfer`, `MetabolismSystem` and `TickHooks`.
- Ongoing blood-loss damage can also occur independently. A healthy-body fixture
  with pure blood and no fall/bleeding now confirms delayed typed poison damage
  through actual timed ingestion and scheduled metabolism, with an untreated control.

Validation: **2,125 unit tests passed**; focused timed-interaction fixtures:
**14/516 selected, all 14 passed**, zero untagged. Evidence:
`build/timed-connected-02-unit.log`, `build/timed-connected-02-fixture.log`.
Independent input review passed. Successful disposable fixture world removed.
Full suite not rerun for this narrow follow-up; prior full-suite failures remain
unresolved. No puddle speed or blood chemistry changes were made.

Restart client/server and retest Alt+E with an empty active hand on a puddle;
ordinary E and Shift+E should retain their existing actions.
