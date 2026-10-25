# Animal checks

For a fresh-world flight check, run `/ms14 animal_fixture setup`. Watch for 45–60
seconds: flyers should first walk/rest, then travel through multiple airborne
goals with smooth ascent/descent and deliberate landing. Leaving an open-top
enclosure is expected; ceilings are actual obstacles, not an invisible cage.
Quick-check possession jump, sneak and neutral controls, plus slow grounded
movement. This brief check is not owner acceptance.

For the owner airspeed comparison, watch a bat fly, then possess that same species
and compare forward+sprint air pace with its slow grounded movement. This is a
subjective manual check; automated normal-AI comparison does not prove possessed
control or visual feel. Bee/butterfly pickup and throw instructions below remain
separate; do not use them as bat instructions.

Throw-recovery candidate check in a fresh world: run `/ms14 animal_fixture setup`,
then have a helper pick up and throw a conscious, dry bee or butterfly with clear
headroom. Observe its initial impulse and whether it catches into flight. Bat
flight and possession checks are separate: bats do not have `PortableBody`, so do
not attempt to pick up or throw a bat. Real bee/butterfly pickup and throw recovery
remain unverified until manually checked. Check fixture `status`/`clear` as usual.

Current command coverage is 37 animals, including a mouse candidate that is not
yet native-validated; it is not a 36-entry manual summon list. Treat counts as
candidate coverage, not evidence of runtime/native validation.

Candidate setup and expected results. Gameplay/owner checks below are for use after
technical readiness; this document is not a gate receipt or owner acceptance. The
isolated sustained diagnostic capture may run earlier as coordinator validation;
it is not a feature-gate PASS or owner acceptance. Do not rewrite or treat the
gameplay checks below as diagnostic proof.

Use an operator account and a safe, enclosed test area. Keep a second player/body
available for hand, equipment and analyzer checks. Use `/ghostroles` to select
animal entries. Entries marked `[DEV]` are operator-only development offers, not
ordinary SS14 GhostRoles; ordinary role entries retain their existing eligibility.
For a fresh-world showcase, run `/ms14 animal_fixture setup`, then `status`, and
`clear` when finished. The command requires operator permission and a committed
character, places a bounded enclosure nearby, and clears only its owned animals
and unchanged placements. Release the bat from a safe spot and retest turning and
cruise flight after the population is active.
Bat has no upstream GhostRole component, so its `[DEV]` listing is a deliberate
local operator-only adaptation.
Before animal checks, join normally: the camera must bind to the character without
showing the inside of its head; move/jump/look must control that character, with no
readiness kick. Stop on failure. Do not delete worlds or edit lifecycle profiles to
work around a failed join. Automated server Ready fixtures do not prove this step.
Use disposable test bodies: `/suicide` kills the currently controlled body,
including a possessed animal. Re-enter ghost control before each new takeover;
select the next body through `/ghostroles`.

Latest feedback retest (after the corrected candidate clears validation):
- Have a second player watch a `[DEV]` bat takeover. Confirm Request completes,
  movement is visible to both players, and releasing input does not snap back or
  keep sliding. Compare an ordinary animal role.
- Observe autonomous flyers through several travel/wait cycles: they may wait
  aloft and continue flying. Check facing during braking and feet after landing.
- Compare the requested smaller species, both reindeer, and slightly smaller
  spiders. Gorilla artwork should be unchanged; inspect its collision outline
  with **F3+B**. Inspect the monkey diaper from several angles.
- With Friendly Creatures audio enabled, possess a voiced animal such as parrot
  and send local IC speech (plain, `?`, `!`). Silence from most idle NPC species
  is not by itself a failed speech-sound test.

For the cleaning check, prepare a blood puddle before possession: use
`/ms14dev creative`, obtain
`/give @s moonstation14:bottle[moonstation14:reagent={"moonstation14:blood":10.0}]`,
then sneak-right-click the top of a solid floor with the bottle. Use
`/ms14dev return` to reclaim the parked character before entering ghost control.
Keep the puddle away from an uncontrolled moproach, or have the helper replenish
it after possession; an unclaimed moproach may clean it autonomously.

1. Summon representative bodies: `/summon moonstation14:bat`,
   `/summon moonstation14:kobold`, `/summon moonstation14:hamster`,
   `/summon moonstation14:cow`, `/summon moonstation14:parrot`.
    Check distinct models, stationary/moving poses and species names on the analyzer.
   Before taking a bat, observe autonomous flight in a room with headroom: it should
   ascend, flap and reach varied goals without shuttling or tight circling after a
   nudge. Ground animals' walking should ease in/out and settle promptly on stopping.
2. Enter committed ghost control (`/suicide`, then move), open `/ghostroles`,
   expand `moonstation14:bat`, select its `[DEV]` entry and choose Request.
   Jump should launch/ascend; crouch should descend and land on a solid floor.
   Release controls while airborne, then land and remain grounded without jumping.
3. Repeat possession with `kobold` using a disposable body. Check its one hand:
   left-click a loose item or portable animal with that hand empty to pick it up;
   use, drop and
   equip an admissible item. Other players should see the same body and held item.
   Check hamster pickup/storage/transfer and its three-second escape; it must remain
   the original creature, not spawn a replacement or display a mouse.
4. As a character with hands, summon an empty beaker at a clear nearby spot:
   `/summon moonstation14:loose_item ~ ~ ~ {Item:{id:"moonstation14:beaker",count:1}}`.
   Left-click it with an empty hand to pick it up, then aim at a living cow and
   press **Alt+E**. Wait for milk production if empty. After five
   uninterrupted seconds, milk should enter the beaker; moving, taking damage or
   losing access should cancel without transferring milk.
5. Control a chicken or duck and run `/layegg`; expect one loose egg and a successful
   action cooldown. Control a moproach near a puddle and use `/cleanpuddle`; small
   quantities should be cleaned without deleting the whole puddle. Let a parrot
   hear nearby local speech and allow time for learning and replay. Provoking a
    configured defensive animal may cause retaliation;
    unprovoked animals must not proactively attack.
6. Repeat the population/load case from the rejected test session. Walk and jump
   among roughly 40 mixed animals, then compare 100 parrots and 100 kobolds; watch
   for several minutes and walk away/back. Report any increasing stalls separately
   from server tick slowdown. Short stationary profiling has not proved this case.

Pending final validation: connected takeover/camera/prediction, native rendering,
held-item alignment, equipment fitting, timed cancellation and custody. Animal
clothing meshes and first-person replacement arms are not supplied; an admitted
equipment slot does not imply a fitted animal clothing mesh.
Large upstream subsystems such
as breeding/fertilization and Diona humanoid reform are not implied by these checks.
# Sustained operator population capture

The coordinator/operator may schedule this isolated diagnostic before feature-gate
PASS; it does not authorize owner acceptance or establish lag resolution. Run each
scenario sequentially with a real owner join and normal physical keys/chat:

```powershell
./tooling/validation/connected_join.ps1 -PopulationScenario Mixed -PopulationSustained -WallTimeoutSeconds 900
./tooling/validation/connected_join.ps1 -PopulationScenario Parrots -PopulationSustained -WallTimeoutSeconds 900
./tooling/validation/connected_join.ps1 -PopulationScenario Kobolds -PopulationSustained -WallTimeoutSeconds 900
```

After the connected diagnostic character is committed, an operator must use normal chat instructions and physical controls to remain near the pen, move, jump, travel away, and return. No input injection, teleport, or readiness bypass is used. The five sustained phases are each 1,200 server ticks after a 100-tick baseline; a slow server therefore takes proportionally longer. A recorded coverage result is not a performance pass, lag-resolution claim, or owner acceptance. Client frame intervals are not camera-pixel or physical-keyboard evidence. The existing 16x16 pen is placed roughly 20 blocks forward from the body; near/outside-pen evidence is not an animal-visibility claim.
