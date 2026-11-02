# Controlled-body emote attribution

**Historical workaround, superseded by the character-identity mini-sprint.**
The profile-harness class distinction below was rejected as an ownership policy.
See `character-identity-mini-sprint/HANDOFF.md` for the accepted body-owned key,
shared lookup, transfer cleanup and validation boundaries.

Owner report: after a human died and the player took a Kobold role, screaming
displayed the dead human's name. The owner accepted this correction and closed the
sprint on November 1, 2026, requesting no validation submission. Native/client
integration was not independently established by automated checks.

`EmoteEffect` already formats the authenticated body through `CharacterPublicName`.
Mapped native bodies retain an account/profile lifecycle binding for control;
that binding must not grant the previous human's saved personal name. Saved
account/profile names apply to `PlayerCharacterHarnessEntity` profile bodies,
including their corpses. Native bodies retain resolved metadata/native names,
with explicit custom names taking precedence for every body.

Reuse the shared resolver; do not add scream-only names, alter ownership bindings,
or change vocal profiles. Ordinary local-speech identity packets use a separate
ledger path and are outside this correction.

Focused validation passed all three source-set compiles and 11 tests in five
suites. `CharacterPublicNameTest` exercises the real public-name/emote path with
the old human ledger binding, dead profile-harness identity, and native custom
names. Evidence is under ignored `build/emote-identity-fix/`. No new workflow
submission was made for this correction. The earlier interrupted melee submission
settled as source-mismatch ERROR and remains a separate recovery boundary.

Manual retest: die as a named human, take a Kobold role and scream. The line should
name the Kobold, not the dead human. Check that the human corpse keeps its name;
an explicitly custom-named creature should use that custom name for emotes.
