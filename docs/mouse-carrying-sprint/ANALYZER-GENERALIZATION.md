# Analyzer extension contract

This follow-up addresses the owner's generalization objection to r43. R43 passed
its technical gate but was not owner-accepted. Its saved evidence is unchanged.

## Shared owners

- Medical state remains owned by `MobStateSystem`. Analyzer readings require a
  resolved medical policy and use the canonical state query, including default
  Alive without allocating an attachment. Unknown is retained for unsupported
  policy, not inferred from native entity type.
- Species remains resolved typed `Species` composition, with existing humanoid
  fallback. A JVM contract discovers **all** project character JSON resources
  recursively using the configured `moonstation14.projectDir`, resolves normal
  inheritance, and checks concrete host-bound medical definitions. It has no
  Human/Pig/Mouse resource allowlist. A future unlabeled fixture fails the same
  validator with its prototype ID.
- `CharacterVisualState` supplies an immutable visual snapshot.
  `CharacterVisualService` uses one capture path for detached application and
  equality. Mouse and Human harness capture their own appearance fields already
  consumed by their normal renderers. Native Pig/Villager adaptations live once
  in that shared presentation owner. Native Sheep is supported there as well,
  capturing dye color, sheared state, and baby age. Equipment copying is shared.
- `MedicalPortraitSnapshot` has no per-mob type branches. It delegates detached
  creation and appearance comparison, retaining neutral medical pose/health
  normalization on the detached copy only. Normal native renderers remain in use.
  Neither whole save NBT nor indiscriminate synced authority data is copied.

## Adding a supported mob

Declare/inherit medical composition and a species label in its character
prototype. Put custom visual capture alongside the entity's appearance owner,
not in the analyzer. Reuse a shared native adapter for native-host variants.
The supported-host fixture must reject a generic fallback as proof of complete
appearance: a future vanilla-hosted animal with variants needs explicit shared
appearance support too. Logical `minecraft:player` is tested through the actual
Human harness presentation host rather than an unsupported native player factory.
The test-only `posture_fall_audio_fixture` intentionally binds its sheep to the
Human host because identity enrollment requires host ownership; removing that
binding or rebinding it conflicts with the real host index. Sheep therefore uses
the shared native visual adapter rather than weakening the appearance guard or
adding a fixture-specific analyzer exception.

The catalog/native contracts enumerate bindings; adding a JSON definition must
not require editing a fixed test list. Missing species, missing factories, and
missing complete appearance support should fail with the prototype/host identity.
Existing Mouse/Harness variant mutations exercise owner capture and invalidation;
an unlisted native Cow exercises the generic fallback outside supported medical
bindings. No new synthetic registered custom entity was added solely for tests.

## Limits and validation

This is not automatic discovery of arbitrary renderer fields or third-party mod
compatibility. A new appearance owner must implement its visual snapshot once;
the analyzer requires no new mob-specific branch. Contract tests catch omission,
not every possible bug in a newly written renderer or snapshot implementation.
Fresh native/readiness/client validation and manual portrait checks are required
before presenting this follow-up as technically ready. GPU visual quality and
connected physical input remain owner checks.

## Permanent-revival warning

`MobThresholds.allow_revives` controls whether ordinary healing can undo Dead;
it does not mean explicit revival is impossible. Do not derive the analyzer's
`unrevivable` alert from that flag, current death, or damage totals. Pinned SS14
uses an explicit `UnrevivableComponent` with `Analyzable` for this warning.
The port has no equivalent permanent marker yet, so current readings omit the
warning. This neither implements defibrillation nor promises a successful revival.
