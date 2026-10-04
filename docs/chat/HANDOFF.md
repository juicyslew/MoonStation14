# Local chat

## STATUS

Reconstructed 2026-10-03. Local speech is implemented; the historical sprint handoff leaves full two-client/pixel acceptance open. Its pending-merge description is historical, not current worktree status. Later hands/HUD work changes compact-feed sizing and cursor visibility; consult `../hands-inventory-program/HANDOFF.md` for those accepted adjustments.

## ARCHITECTURE

Server-authorized committed CHARACTER speech uses body identity, bounded acoustics, and explicit packets. Client transcript, bubbles, close callouts, and edge tints consume that evidence. Humans require the Speech component. U exposes a read-only interleaved feed; vanilla history is visually hidden, while T input, commands, signatures, and logging remain.

## IMPORTANT FILES

- `sprint-handoff.md`: concise policy and validation; `current-status.md`: detailed history.
- `src/main/java/com/juicyslew/moonstation14/ms14/chat/{server,identity,network,client}/`.
- `mixin/client/{ChatComponentUnifiedViewMixin,ChatListenerSystemNoticeMixin}.java` below the same Java package root.
- Chat unit tests and `LocalSpeechOcclusionGameTests`.

## SS14 CORRESPONDENCE

Local speech is a Minecraft adaptation of server-routed speech, not implemented radio/telecom. SAY/SHOUT range is 15 blocks; whisper is clear through 3, muffled through 6. SHOUT changes presentation, not range. Upstream anchors: `Content.Shared/Chat/SharedChatSystem.cs`, `Content.Server/Chat/Systems/ChatSystem.cs`.

## DECISIONS / WHY

Identity fails closed; never substitute account names or allocate identity during speech. Radio-prefixed attempts become local whispers. Muffled packets intentionally disclose presented name, entity ID and UUID, but mask body text and provide coarse direction, not XYZ. This is not anonymity.

## KNOWN GAPS

Historical latest callout alignment, visible-speaker cue suppression, SHOUT styling, and complete acoustic/privacy behavior lack a recorded full connected acceptance. Matching client/server builds are necessary after packet changes. Remote radio, deaf accessibility, and voice/damage cues remain deferred.

## VALIDATION

Historical combined evidence: 1,563 JUnit tests passed and 319/319 required GameTests after fixture cleanup. Earlier speech-only runs include a finite-status-expiry failure and a later 220/220 pass before a presentation-only change. No tests or pixel review rerun for reconstruction.

## NEXT WORK

Recheck the latest hands/HUD presentation before using old visual checklists; perform targeted two-client acoustic/privacy regression and owner presentation review if extending chat.
