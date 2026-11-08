# Current-character identity candidate

**Accepted and closed by the owner, November 2, 2026.** Final gate
`run_4e3a3c7c7a46005c60176e38` passed. `HANDOFF.md` supersedes candidate-status
statements below; pending notes are historical, not requests for another run.

Implementation and focused checks are complete; serialized OWNER_READY validation
is pending. No owner acceptance or connected-client result is claimed.

## Speech feedback revision

The preceding complete candidate passed as `run_a9302bfb855791728e4f7223`.
Owner testing then found that Kobold typed speech did not arrive. The existing
accent conversion was already correct; the later speech-packet constructor still
accepted only two-word human or numbered-NPC names, rejecting the valid public
name `Kobold`. Ledger entries, owner identity packets and speech packets now share
one character-name validator and the existing 80-character bound, including codec
limits. No species-specific admission rule or English-speech bypass was added.

Muffled whisper delivery also now derives its text from the already-transformed
speech payload, not the original intelligible input. Shared range/bearing,
zero-coordinate, white-color and no-audio privacy rules remain unchanged.

Current follow-up preflight: all three source sets compile; 22 exact source/XML
test suites passed, 209 tests with zero failures/errors/skips. Evidence is in
ignored `build/character-identity-mini-sprint/speech-final-preflight.{log,json}`.
Independent source review passed. Native execution and owner retest of this
revision remain pending; preceding evidence below is not a new revision PASS.

The native `koboldTypedSpeechUsesReplacementAndCurrentBodyName` fixture posts a
real registered-player `ServerChatEvent` after adoption and checks delivery of the
current body name, one of the nine Kobold replacements, and the Lizard sound cue.
It is tagged `animal-speech`; previous transfer/return coverage is retained.

### Monkey scream question

No separate Scream action is normal for pinned SS14's Monkey. Its full chain is
`MobMonkey → MobBaseAncestor → [BaseMobAnimal, SimpleMobBase, StripableInventoryBase]`;
it has Speech/MonkeyAccent but no Vocal component. `VocalSystem.OnMapInit` grants
the scream action only to Vocal entities. Local resolved-composition tests retain
that distinction; no Monkey action or asset was invented.

Kobold resolves through `MobKobold → MobBaseKobold → MobBaseAncestor`; its existing
Speech/ReplacementAccent/Vocal data remains unchanged. Exact full replacements
match pinned `Resources/Prototypes/Accents/full_replacements.yml:157–169` and
`Resources/Locale/en-US/accent/accents.ftl:112–120`: Yip!, Grrar., Yap!, Bip.,
Screet?, Gronk!, Hiss!, Eeee!, Yip. The animal parent contributions are indexed by
`animals.yml:1350–1478,1550–1569`, `Body/Animals/animal.yml:42–55`,
`Mobs/NPCs/simplemob.yml:66–74`, and `InventoryTemplates/inventorybase.yml:8–17`.

## Current ownership

- Account and mind remain stable. Taking a different character replaces the
  durable/current profile key with `PlayerCharacterBinding.profileKeyForBody` for
  that target UUID. Store, registry indices, session and body binding agree.
  The old character keeps its own identity on its corpse; the new binding has no
  old character key. Reconnect and Creative return retain the current key.
- The existing store permits that change only for the constrained ghost/dead-claim
  to new active-body transition, with unchanged mind and increasing generation.
  Failed preflight/enrollment/CAS does not authorize a new character.
- Body identity is durably established before takeover. An already named NPC
  retains its exact body-UUID identity; otherwise the existing character ledger
  enrolls the target's own public name under its new key. Identity queries never
  allocate. Human name generation and shared color/name claims remain in the
  existing ledger; no parallel identity registry or historical reporting is added.
- `CharacterPublicName.savedIdentity` is shared by public naming, speech and owner
  identity delivery. The former profile-harness class filter is removed. Custom
  names retain precedence; initial, later humanoid and native bodies follow the
  same lookup policy.
- Identity packets carry body UUID, epoch and dimension. Ownership transitions
  clear live client identity without deleting transcript history; stale packets
  cannot restore an old body's identity. Successful adoption synchronously clears
  the exact old medical observer/corpse-return context and old-body hint.

## Validation and boundaries

Corrected focused preflight: 18 source-verified JVM classes / 188 tests, zero
failures/errors/skips; all three source sets compile. Fresh XML suite identities
match every requested selector exactly. Evidence: ignored
`build/character-identity-mini-sprint/corrected-preflight.{log,json}` and
`corrected-junit/`. Independent lifecycle and identity-delivery reviews passed.
Separate required-readiness fixture repair: `LooseObjectProfileTest` now derives
prototype keys from resource filenames, matching production loading of main's
id-less JSON definitions; all 12 tests passed. Return-fixture tag metadata compiles.

Native selection: `character-identity-transfer` (existing Kobold/bat registered
adoption fixtures), `character-identity-return` (11 return/park/Creative boundary
cases), `lifecycle-reconnect-lease`, and normal ghost-role/development-possession
boundaries. Execution belongs to the serialized gate, not the author.
Readiness: `interaction`; UI painting and prediction algorithms were not changed.
The explicit identity packet/cache tests cover the changed server-authoritative
delivery behavior.

An accidentally unfiltered author test command ran 4,931 tests and reported 13
failures outside the identity changes. The readiness-blocking filename-key fixture
was corrected; other resource-hash/stale-fixture failures remain outside this
mini-sprint. This is not a full-suite PASS. Bounded failure evidence is retained in
ignored `build/character-identity-mini-sprint/`; the intended filtered preflight
passed afterward. No interactive client was launched.

The first readiness submission, `run_ded6df002160530ab6f66203`, ended in
`EVIDENCE_UNVERIFIED` because three unit selectors used incorrect package names.
MAIN confirmed 15 classes / 169 passing tests in that partial run, not complete
coverage, and no source drift. Its ERROR was retired through owner-authorized
recovery; it is not a PASS and must not be replayed. The corrected preparation
checks source package/class declarations and exact fresh XML suite identities.
Earlier count-only preflight summaries are superseded by the corrected evidence.

## SS14 and scope

Reference verified at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`:
`Content.Server/Mind/MindSystem.cs:170–337` separates mind transfer and user
assignment from entity identity. Upstream `MindComponent` also retains original
ownership/name information; the owner explicitly requires that such history not
serve as current-body identity. No new history subsystem, prototype component,
save migration, sound, sprite, role eligibility, speech permission, acoustics or
movement behavior is introduced. Existing resolved metadata supplies body names.

## Manual check after PASS

Repeat the original human → ghost → Kobold transition and scream: it must name the
Kobold, while the corpse retains its human name. Type one ordinary sentence as the
Kobold: listeners should receive a Kobold replacement utterance, not the original
sentence, with the correct body name. Existing custom names should remain unchanged.
