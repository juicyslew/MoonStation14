# H3 persisted hands: bounded proof and limits

This tranche adds a persisted and synchronized body attachment for a bounded set of named hands, active-hand selection, and opaque item-token identities. Its immutable component rejects empty/oversized hand sets, duplicate hand IDs, duplicate tokens, invalid active-hand references, and overlong identifiers. The stream codec checks the hand count before allocating its collection; the data codec and value constructors reject malformed saved state rather than silently overwriting it. Collection snapshots are copied and immutable.

## Explicit non-goals / safety boundary

This is **not an inventory** and does not store Minecraft `ItemStack`s. Tokens are opaque identifiers only: this work does not prove that a token corresponds to an item, that an item was consumed from a source, or that hand state can safely materialize an item. No insertion/extraction or transfer API is exposed. In particular, callers must not treat the token model as proof of item conservation. A real item-bearing implementation remains blocked until an authoritative server transaction validates the exact controlled body/current harness and consumes/returns the source stack atomically.

The attachment has a default empty left/right state for NeoForge's supplier, but registration does not attach it to entities. Callers that need a read-only default should use a detached value; do not call `getData` merely to inspect an unbound entity. The generic provider/bridge exposes the normal repository bridge pattern, so any future mutation entry point still needs a body/harness authorization boundary.

## Persistence evidence and remaining gap

The component and attachment codecs are covered by JSON/NBT round-trip tests, malformed-state checks, defensive-collection-copy checks, and a bounded stream-codec round trip/rejection test. `HandsStorageGameTests` exercises NeoForge entity `saveWithoutId`/`load` attachment persistence and verifies untouched entities remain without a hands attachment. This is a dedicated-server GameTest source but does not simulate a complete world shutdown/restart save cycle; compile success alone is not a claim that the GameTest was executed.

The pure domain now applies the same 64-character hand-ID and 128-character opaque-token limits as persistence, with the canonical bounds declared by `HandState` and `ItemToken` and reused by `HandComponent`. Values within those bounds remain valid in the domain; this does not change serialized formats or semantics.

## Final validation record

The coordinator ran `.\gradlew.bat test compileGametestJava --no-daemon` after the latest hands bounds and grid changes; it reported `BUILD SUCCESSFUL`. The coordinator also ran `.\gradlew.bat runGameTestServer --no-daemon -Pms14GameTestDir=build/gametest-hands-proof-final`, which reported `BUILD SUCCESSFUL`. At 18:56:42, `build/gametest-hands-proof-final/logs/latest.log` contained `160 GAME TESTS COMPLETE` and `All 160 required tests passed :)`.

That 160/160 GameTest run was before the later `HandComponentTest` change adding an invalid optional token-only case. After that test change, a worker ran the focused `HandComponentTest` and `compileGametestJava` successfully. The 160/160 result is not claimed as a rerun after the added test. The dedicated-server GameTest exercises entity `saveWithoutId`/`load` attachment persistence and untouched entities without an attachment, but not a complete world shutdown/restart save cycle.
