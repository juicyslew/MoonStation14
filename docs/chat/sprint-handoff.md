# Final local-chat sprint handoff

**Authority:** this is the concise current implementation/design handoff. The [working-tree history and detailed validation chronology](current-status.md) is retained separately; older implementation descriptions there are historical where marked superseded. Main is merged into the `agent/03` working index and conflicts are resolved, but the merge is **not committed**; the owner controls commit. Pixel acceptance is incomplete.

## Integration status

The merged component refactor makes speech authorization explicit: humans have the `Speech` component; pigs do not. Main's component-based host-plus-components schema, hands, respiration, chemistry, and organ prototype registry are preserved alongside the radio catalog. Fresh combined validation passed: `compileJava compileGameTestJava test --no-daemon` (1,563 JUnit tests). Isolated required GameTests passed 319/319 at `build/gametest-chat-component-merge-04`, after cleaning leaked `FakePlayer` test fixtures; earlier `-01`/`-02`/`-03` runs each had one fixture-contamination failure, not a product finding. Fresh connected client/server pixel validation remains outstanding. No remote radio is implemented.

## Scope and decisions

- Only exact committed `CHARACTER` local speech is server-authored and unsigned. Authorization and identity fail closed; no account/profile names and no on-demand identity allocation. Slash input and vanilla command transport remain intact.
- SAY and SHOUT use 15-block body range and bounded sound checks. SHOUT changes presentation, not range. Whisper `,` is clear through 3 blocks, muffled through 6, then absent. Acoustic checks are bounded, do not load chunks, and account for solid thickness plus exposed floor/roof crossings; see current-status for exact probe/floor rules.
- `;` and valid `:key` attempts are retained as one ordinary local whisper. No remote transmission is attempted; radio equipment/telecom authority is not implemented.
- Muffled delivery deliberately exposes presented name, entity ID and UUID to recipients. Its body is masked; packet direction is coarse (16 horizontal sectors, five vertical bands, coarse distance tier), with no XYZ and no durable RGB. This is not anonymous. Identity ledger failures/missing entries fail closed; do not substitute account names.
- U is a default-visible, nonmodal, read-only interleaved feed for character speech and accepted notices. Vanilla history is visually hidden even for uncommitted player chat; history storage, slash transport, suggestions, signatures and logging remain. U is not text entry. Uncommitted chat is not promoted to authoritative character speech.

## Current presentation policy

- Other-speaker world bubbles: up to four per speaker, 4–10 seconds by text length, with transient bold presented-name tab (not an overhead name tag). Tracked placement is UUID-bound; loaded-chunk-safe visibility applies.
- Close callouts: tracked UUID-matched active speakers only, near hysteresis, max two, newest utterance per speaker. Center header and every body row. Use validated head/body projection and viewport checks; no rear/offscreen/occluded rescue. U keeps full text.
- Edge tints: max three visible from queue of 12, mode-specific SAY/SHOUT versus whisper distance curves. Whisper is thin/sharp (18–38 GUI px, exponent 8); owner says it now looks good. Only matched listener-name words get RGB mention color in U/bubble, never the whole line or masked text. Suppress directional cue for receipt-visible tracked body currently within viewport, including mentions; retain cues for occluded/offscreen sources.
- SHOUT body is bold; inline U/history presented-name and `yells:` verb are not bold. The transient bubble name tab intentionally remains bold.

## Implementation landmarks

- Server authority, policy and identity: `src/main/java/com/juicyslew/moonstation14/ms14/chat/server/`, `.../chat/identity/`, and chat event handling in `src/main/java/com/juicyslew/moonstation14/MoonStation14.java`.
- Wire contracts and client view/rendering: `src/main/java/com/juicyslew/moonstation14/ms14/chat/network/` and `.../chat/client/`; client registration in `src/main/java/com/juicyslew/moonstation14/MoonStation14Client.java`.
- Vanilla history suppression and accepted-notice mirroring: `src/main/java/com/juicyslew/moonstation14/mixin/client/ChatComponentUnifiedViewMixin.java` and `ChatListenerSystemNoticeMixin.java`.
- Focused test coverage: matching `src/test/java/com/juicyslew/moonstation14/ms14/chat/` packages and `src/gametest/java/com/juicyslew/moonstation14/gametest/LocalSpeechOcclusionGameTests.java`.

## What has and has not been observed

Owner-connected observations: baseline SAY/SHOUT/whisper works; bubbles exist and are decently placed; broad tint tracks well; latest whisper tint looks good; close callout is mostly good. These are specific impressions, not acceptance of the latest full presentation.

Not visually accepted after the latest refinements: centered callout rows/header, suppression of mention direction cue for visible tracked speakers, final inline SHOUT bold policy, complete two-client acoustic/UI/privacy behavior, and full GUI/edge/tint/bubble regression. Fresh matching client and server builds are required because the speech payload changed. Do not claim pixel acceptance.

## Validation and owner actions

- Earlier speech-only automated result after final style change: `./gradlew.bat compileJava compileGameTestJava test --no-daemon` — 1,420 JUnit tests, 0 failures/errors. The fresh combined result is recorded under Integration status above.
- Latest isolated run `./gradlew.bat runGameTestServer --no-daemon -Pms14GameTestDir=build/gametest-chat-visible-mentions-01` completed 220 with one finite status-expiry failure (`boundhumanplayerandvillagersharetimedstunpolicy`). Fresh `...visible-mentions-02` passed 220/220 **before** the final presentation-only bold change. Failure root cause is unknown; preserve both results.
- Owner follow-up: inspect latest callout alignment/visibility, mention cue suppression, shout styling, and two-client acoustic, privacy, feed, GUI and bubble behavior. Confirm client/server match. Radio equipment authority/telecom, safe identity-ledger migration, distant bubble readability, voice/damage cues and deaf accessibility are deferred, with no commitment.
- The merge is resolved in the working index but remains uncommitted. The owner controls commit; this handoff does not authorize staging or committing additional changes, or discarding any work.
