# Owner check: operational residency landing and reconnect

Run the normal dedicated-server project launch on a disposable flat world with
clear ground; do not edit owner configuration. The launch ensures six Sable
substeps. This check verifies landing/reconnect while the operational frame
remains resident; it does **not** test chunk unload (policy keeps it resident).

1. Run `/ms14 shuttle_fixture build` and record the reported ship ID. Run
   `/ms14 shuttle_fixture status ID` and confirm `sdkSubstepsPerTick=6`.
2. Board the cabin. Run `/ms14 shuttle_fixture cargo ID` three times to spawn
   three water jugs (5 units each). Leave all three on the deck and note their
   contents; do not pick them up. If you already have an identifiable item held,
   note it as an optional held-item check; otherwise omit that check.
3. Run `/ms14 shuttle_fixture nudge ID 0 20 0`. Disconnect immediately while
   ascending (within about one second). Leave the dedicated server running for
   15 seconds, then reconnect; do not reset or replace the frame/body.
4. Confirm the ship has landed and the same body is alive in the cabin, with all
   three deck jugs/contents present and no damage. If testing an already-held
   item, confirm it remains held. Report any
   displacement, clipping, loss, or failure to land/reconnect.

If the ID is no longer uniquely loaded, run `/ms14 shuttle_fixture list` and use
the exact saved ID only if it identifies the same ship. This bounded check does
not establish high-speed flight, hull integrity, complete M1, or M2–M6 readiness;
those remain unverified/pending owner evidence. Automated PASS is not owner
acceptance.
