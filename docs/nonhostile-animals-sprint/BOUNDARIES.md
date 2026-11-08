# Scope and parity boundaries

Reference: SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
`PROTOTYPES.md` indexes the resolved animal profiles and relevant source files.
This document records implementation boundaries, not acceptance or test results.

## Deliberate adaptations

- SS14 flying mobs have 2D airborne collision/movement composition. Minecraft
  takeoff, vertical travel, hovering and floor landing use the shared movement
  owner and jump/crouch input. No second native flying navigator is introduced.
  The accepted flying rigs have no grounded walking clips; landed translation
  uses their existing idle pose rather than inventing animation assets.
- Upstream circular fixtures determine horizontal collision width. Vertical body
  extent is a Minecraft adaptation; changing collision height does not rescale art.
- Chicken/duck color variants currently share a host with persistent prototype-
  allowlisted appearance selection. Poppy and moproach instead have separate
  profiles because they are actual named/composed upstream entity prototypes.
- Existing animal organ/metabolism services are reused. Shared pig-lung topology
  is not evidence of species-specific anatomy or complete atmos fidelity.
- Egg laying uses the existing Minecraft egg as a loose world item, and `/layegg`
  supplies a controlled-character action where the port lacks the SS14 action bar.
  This does not implement egg nutrition, fertilization, incubation or offspring.
- `/ms14dev possess` is an operator-only testing route through the existing
  lifecycle. It does not make every animal an ordinary ghost role.

## Larger unsupported subsystems

This sprint does not add breeding/population management, species-specific
butchering/gib products, Diona humanoid reform, scurret-specific action sets,
spider webs/chemical-injection combat, gorilla environmental destruction, or a
general damage-modifier-set implementation. These require runtime owners beyond
the animal host/prototype, movement, interaction, chemistry and inventory paths
being extended here. Do not represent them by unused prototype components or
claim their parity from a matching model/health threshold.

Resolved melee data currently drives defensive NPC attacks. The port does not
yet provide a general controlled-body empty-hand melee action; importing an
animal's bite/claw statistics does not claim that player-action path exists.

The generic medical/analyzer, chemistry, Mind, custody and control lifecycles
remain shared services; missing features above are not replaced with species-name
branches in those services. Existing item catalogs also limit available wearable
gear: an upstream whitelist is retained even when no compatible item is ported.

## Artwork versus gameplay scope

R4 is the accepted base; saved R5 overrides replace their matching IDs. The scoped
transport imports 43 dependency-complete appearance groups plus attribution and
binding records. Existing accepted mouse files are excluded. Hostile-only groups
are excluded, including rats, space bears/cobras/spiders and angry/Syndicate
variants. The ordinary tarantula is included because its resolved NPC behavior is
neutral, despite combat-capable composition.

The pinned sources do not select `chick`, `penguin_baby` or
`penguin_shamebrero` as concrete mobs or runtime transitions. Their accepted assets
can remain unused; no growth/hat behavior is inferred from texture names.

## Validation and acceptance

Source review and focused JVM checks do not establish connected control, native
rendering, visual quality, authenticated server admission or owner acceptance.
New native fixture source must be compiled and executed by the serialized lead;
affected readiness categories and broad-regression checks belong to that final
gate only after all planned runtime wiring and owner setup are complete.
