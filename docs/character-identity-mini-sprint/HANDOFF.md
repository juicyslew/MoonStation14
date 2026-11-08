# Current-character identity ownership

## STATUS

**Closed by owner acceptance on November 2, 2026.** The owner confirmed the final
Kobold speech correction works. The complete readiness gate passed before that
confirmation. Closure does not imply every manual scenario was individually
observed. No further work, validation, commit or merge is authorized by closure;
changes remain for the owner's merge process.

## ARCHITECTURE

- Account authentication, mind/control ownership and character identity are
  distinct. All character bodies are mobs; initial creation versus subsequent
  possession and concrete harness class do not select a naming policy.
- Taking a new character replaces the current persisted profile key with
  `PlayerCharacterBinding.profileKeyForBody(targetUuid)`. The account and Mind ID
  remain stable. The saved row, registry record, `byProfile` index, session and
  target binding agree on the new key. The old corpse retains its own binding and
  identity, not a live current-character claim.
- Store validation permits key rotation only for the constrained ghost/dead-claim
  to new active-body transition: changed body, canonical target key, increasing
  generation, unchanged account/Mind. Failed preflight, identity enrollment or CAS
  does not authorize the target. Reconnect and Creative return reuse the exact
  current body's saved key rather than generating another identity.
- Identity is durably established before takeover. An existing NPC identity is
  re-ensured durably and preserved by exact body UUID. Otherwise the existing
  character ledger enrolls the target's own public name under its new key.
  Queries never allocate; there is no second identity registry or new history
  subsystem. The ledger's existing name/color claims and human-name allocator are
  reused.
- `CharacterPublicName.savedIdentity` resolves the current binding's entry or the
  exact body's NPC entry. Public names, emotes, ordinary speech and owner identity
  delivery share that policy. Explicit custom names take precedence. The earlier
  profile-harness class filter was removed, not extended to other species.
- Owner identity packets carry body UUID, epoch and dimension. Begin/commit/session
  clearing invalidates live client identity; stale same-dimension packets cannot
  restore an old body's name. Transcript history remains history, not a live
  identity fallback.
- Successful adoption synchronously clears the exact old medical observer
  session, corpse-return hint, intent and cached old-body reference. The medical
  epoch is distinct from the lifecycle epoch: cleanup uses the observer session's
  own credentials. Legitimate corpse return before adoption is preserved.
- Ledger entries and both identity/speech packet codecs share one validated
  character-name contract and the existing 80-character limit. Names need not be
  two-word human names or numbered-NPC names. NPC record numbering remains a
  separate allocator constraint.
- Speech accents run before delivery. Muffled whispers derive from the already
  transformed payload, not intelligible raw input; existing white-color,
  zero-coordinate, no-audio and bearing/range privacy rules are unchanged.

## IMPORTANT FILES

Java paths are beneath `src/main/java/com/juicyslew/moonstation14/`:

- `ms14/player_body_control/lifecycle/PlayerLifecycleRegistry.java`
- `lifecycle/character/PlayerCharacterBinding.java` and
  `lifecycle/persistence/LifecycleProfileStore.java` beneath that subsystem.
- `lifecycle/server/{LifecycleGhostSessionControl,LifecycleCharacterSessionControl}.java`
  and `ms14/medical_observer/ObserverSystem.java`: adoption and tether lifecycle.
- `ms14/chat/identity/{ChatIdentityRegistry,ChatIdentitySavedData}.java` and
  `ms14/character/CharacterPublicName.java`: durable identity and shared lookup.
- `ms14/chat/server/{LocalSpeechServerHooks,SpeechAccentPolicy}.java`,
  `ms14/chat/network/{LocalCharacterIdentityPayload,LocalSpeechPayload}.java`,
  `ms14/chat/client/LocalSpeechClient.java`, and
  `ms14/player_body_control/client/GhostControlClient.java`.
- Tests: `PlayerLifecycleRegistryTest`, `LifecycleProfileStoreTest`, identity
  ledger tests, `CharacterPublicNameTest`, `LocalSpeechIdentityPayloadTest`,
  `LocalSpeechPolicyTest`, `LocalSpeechServerHooksTest`, `AnimalSpeechPrototypeTest`.
- Native coverage reuses `NativeCreatureCombatGameTests`; its real
  `koboldTypedSpeechUsesReplacementAndCurrentBodyName` posts `ServerChatEvent`
  after authenticated adoption and checks the delivered replacement/name/sound.
  Focused transfer/return tags are `character-identity-transfer`,
  `character-identity-return` and `animal-speech`.

`PLAN.md` and `CANDIDATE.md` retain scope and historical candidate evidence.
Related subsystem indexes: `docs/chat/HANDOFF.md`,
`docs/mouse-ghost-roles-sprint/HANDOFF.md`, and
`docs/player-character-lifecycle-sprint/HANDOFF.md`; verify their older status
claims against current code.

## SS14 CORRESPONDENCE

Reference verified at `c9df5ef5d675b0d1d226828bddf6b78c28502d91` in
`SS14-dev/space-station-14`. `MindSystem.TransferTo` separates mind transfer/user
assignment from body identity. Upstream also retains historical name/original-owner
fields; the owner explicitly requires those not to serve as current-body identity.
No round-statistics/history feature was added.

Kobold's existing full-replacement accent matches pinned
`Accents/full_replacements.yml:157–169` and `accent/accents.ftl:112–120`:
Yip!, Grrar., Yap!, Bip., Screet?, Gronk!, Hiss!, Eeee!, Yip.
Its input is accepted but not delivered as the original intelligible sentence.
Monkey's resolved ancestry has Speech/MonkeyAccent but no Vocal component, so it
does **not** receive a separate Scream action. `VocalSystem.OnMapInit` grants that
action to Vocal entities. No Monkey action, sound, sprite or unsupported component
was invented; existing asset attribution remains unchanged.

## DECISIONS / WHY

- Fix live ownership references, not presentation-only symptoms or species checks.
- Preserve an already named target's own body-UUID identity rather than copying it
  into a competing account-keyed record or renaming it during possession.
- Keep account/Mind stable; clear only the superseded character and ghost-tether
  context. No migrations, speculative compatibility or recovery framework.
- The Kobold follow-up was a shared packet-name validation defect, not missing
  permission or accent data. Do not restore human-only name regexes in consumers.

## KNOWN GAPS

This work does not redesign role eligibility, speech permissions, acoustics,
movement, radio, round history or save migration. No interactive client was
launched by the author. Broader unfiltered tests exposed unrelated resource-hash
and stale-fixture failures; the one required-readiness blocker was repaired by
making `LooseObjectProfileTest` use canonical filename-derived prototype keys.
The remaining failures are outside this sprint; no full-suite PASS is claimed.

## VALIDATION

Final acknowledged gate **`run_4e3a3c7c7a46005c60176e38` PASS**:

- 22 exact source/XML unit suites: **209 tests**, zero failures/errors/skips.
- Applicable `interaction` readiness passed; package and provenance passed.
- Filtered native validation: **29/29 passed, 1,407 discovered**. This includes
  current-body adoption/naming, the Kobold chat-delivery regression and selected
  shared return/reconnect boundaries. Tags do not imply isolation or full-suite
  coverage.
- All three source sets compiled; independent implementation/parity reviews passed.

Earlier `run_ded6df002160530ab6f66203` remains a retired ERROR from three incorrect
test-package selectors, not source drift. Corrected preparation verifies source
package/class identities and every fresh XML suite; partial old receipts are not
substitutes for complete coverage. Detailed evidence is coordinator-bound or in
ignored `build/character-identity-mini-sprint/`, not copied into docs.

## NEXT WORK

No active sprint work remains. Preserve these shared owners in future changes.
New requirements need a new agreed scope; do not reopen validation or replay
historical submissions merely to update closure documentation.
