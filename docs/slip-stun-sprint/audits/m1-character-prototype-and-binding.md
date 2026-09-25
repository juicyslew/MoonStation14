# M1 Character Prototype and Binding Report

**Review date:** 2026-09-24  
**Milestone result:** M1 character data and binding proof is implemented and bounded by the evidence below. This is not evidence of live gameplay or a completed slip/stun sprint.  
**Coverage owner:** Project owner.  
**Dated coverage review/deadline:** Pending explicit owner assignment; no date is fabricated here.

## Result

- A typed immutable `CharacterData` family defines `slip_data` as a target-capability bundle, separate from source-side reagent slip data. It represents stun susceptibility, intrinsic no-slip, standing/prone eligibility, and reactive groups/methods. The `moonstation14:human` JSON prototype supplies values for each capability, including acidic Touch admission.
- `PrototypeRuntime` now has four registered/exported prototype types: reagent, status effect, alert, and character. `PrototypeRuntimeTest` covers four-type registration and character export/import between independent catalogs.
- The identity attachment stores only a `ResourceLocation` character prototype key. Policy remains in the prototype catalog. Server join enrollment recognizes server players and Minecraft villagers and requests the same `moonstation14:human` key; policy resolution is against the current server catalog. Identity reads do not materialize absent attachments. Existing mismatched or dangling keys are not replaced, and unresolved identities fail closed.
- GameTests use a `FakePlayer` (a NeoForge `ServerPlayer` subclass), not an authenticated connected client, together with a real `Villager`. They verify server-side join binding of both to the same human key and equal populated policy resolution. They also exercise unadapted-pig inertness, dangling-key non-overwrite/fail-closed behavior, and player clone identity handling with and without death.

The proof establishes server-side adapter behavior for those fixtures and parity of their resolved data. It does **not** establish that an authenticated player connected through the normal login/network path has been tested, nor that identity survives a real dimension transfer or disk save/shutdown/reload cycle. Codec/provider representation and clone tests are not substitutes for those lifecycle proofs. No live control-transfer system or test is claimed.

## Verification evidence and scope

The existing server GameTest output at `build/gametest-run/logs/latest.log` reports `90 tests are now running`, followed by `========= 90 GAME TESTS COMPLETE IN 2.572 s ==========` and `All 90 required tests passed :)` (2026-09-24 00:13:41). Thus the run completed all **90 required GameTests**. The log is evidence of that completed run; this documentation-only task did not launch tests or independently reproduce the run. The log itself does not record the Gradle invocation, so no exact historical command is asserted. The repository's documented GameTest command is:

```powershell
.\gradlew.bat runGameTestServer --no-daemon -Pms14GameTestDir=build/gametest-run
```

Focused tests present in the reviewed source include `CharacterPrototypeTest`, `CharacterIdentityTest`, `PrototypeRuntimeTest`, and `CharacterIdentityGameTests`. This report does not claim a separate JUnit invocation or JSON parse command was run for this report. The M1 GameTests exercise binding and policy identity only; they do not run a slip or reactive Touch scenario.

## Limitations and follow-up gates

- Reactive codec structure is currently **two separate lists**, `reactive_groups` and `reactive_methods`. The data shape can only express a global cross-product policy: each listed method associated with every listed group. That expresses the current human Touch profile, but cannot faithfully express future arbitrary group-to-method associations (for example, Touch for one group and a different method for another) without changing the schema/model. Runtime reactive dispatch is not established by this milestone.
- This milestone does not prove actual slip contact, source aggregation, SlipEvent order, stun/action blocking, physical prone/crawl, or reactive Touch execution. These remain later milestone work, not M1 claims.
- M2 progression is conditional on later real gameplay tests and the remaining milestone gates. In particular, tests must prove actual server-authoritative gameplay behavior; this M1 identity test suite is not a live-client or slip/stun acceptance test.
- Coverage planning remains owned by the project owner. The actor-family adapter roadmap and dated coverage review/deadline need explicit owner scheduling; the pending date is an open follow-up, not a pass criterion silently satisfied here.
