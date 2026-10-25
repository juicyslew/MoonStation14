# Non-hostile animals sprint

## Acceptance target

Implement the remaining non-hostile creatures represented by the pinned SS14
`Resources/Textures/Mobs/Animals` folder. The original mouse remains supported by
its existing implementation. A neutral animal that retaliates when provoked is
included; explicitly hostile variants are not made into peaceful replacements.
Artwork is the owner-accepted saved R4 collection plus its five R5 overrides.
Owner authorized continuing implementation after accepting those revisions.

## Ordered work

1. Resolve upstream ancestry, defaults, AI factions/tasks and appearance variants;
   map the supported composition to typed local prototypes and shared animal hosts.
2. Bind the accepted native model/animation/texture dependency groups to a shared
   renderer and entity-owned visual snapshots. Preserve the accepted mouse files.
3. Extend shared movement with natural flight and landing; configure hands,
   inventory, carrying/escape, physiology, speech and neutral/defensive AI through
   resolved components. Integrate source-specific abilities through existing
   services where practical, and explicitly document unavailable subsystem limits.
4. Finish live wiring, spawn/control access, regression coverage and concise owner
   setup/check steps. Use focused cheap checks throughout. Submit the complete
   sprint to the serialized integration gate only when a pass would make it ready
   for the owner to test; no scaffold or implementation-milestone gates.

## Invariants

- Prototype composition, not species-name branches, owns gameplay capabilities.
  Ordinary monkeys, kobolds and scurrets inherit ancestor hands; other animals do
  not acquire hands merely by using the same host class.
- Portable characters remain original entities through custody, storage, escape,
  throwing, disposal, death and analysis. Upstream `Item`/escape composition decides
  eligibility and timings; the mouse's defaults are not universal animal defaults.
- The shared medical/state and visual-snapshot pipelines serve every character.
  No analyzer-specific creature allowlist or second portrait implementation.
- Natural flight is separate from the existing external throw-flight lease. One
  movement owner integrates each body; containment, medical state and canonical
  control remain authoritative.
- SS14 `FlyingMobBase` describes 2D airborne movement, slip immunity and movement
  permissions, not a takeoff/landing action. Landable vertical flight is an explicit
  Minecraft adaptation. Chicken/duck/goose/penguin are not granted flight simply
  because they are birds. Frog/kangaroo model hopping is not evidence of a new
  physical ability.
- The ordinary tarantula (`MobGiantSpider`) is neutral/idle; its sprite is
  `spider.rsi:tarantula` (`animals.yml:2615–2633`). Angry/Xeno and clown variants are
  distinct and excluded. Rat servant/king/buff and space bears/cobras/spiders are
  hostile and excluded. Combat-capable inheritance alone does not mean aggression.
- Existing native pig/villager/sheep bindings and the mouse are not overwritten.
  Accepted artwork is copied unchanged with attribution; no regeneration to retrieve
  files, mixed revisions, speculative model rescaling or duplicate mouse classes.

## Validation boundary

Planned implementation is present; technical validation remains pending. Source
additions, successful compilation or isolated helper tests do not establish a
playable sprint, parity, visual quality or owner acceptance.
Full JVM/native and affected UI/interaction/prediction readiness belong to the final
serialized gate after required assets and runtime wiring are present. Connected
control, camera, animation and gameplay remain explicit manual-test boundaries.
