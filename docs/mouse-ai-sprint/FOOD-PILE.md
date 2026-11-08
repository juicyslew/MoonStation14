# Mouse food-pile acquisition fix

Food target choice continues to use `NpcTaskPlanner` eligibility and distance utility. At new food-goal acquisition only, it now selects with the mob's own PRNG from at most eight eligible sources within two blocks of the nearest eligible source. Thus marginally similar nearby piles can split across mice without preferring distant food. The normal vision cutoff remains strict (`distance < 10`); source discovery remains loaded-item/AABB based and route checks remain owned by `GroundNpcRoutePlanner`.

An existing eligible food target is retained on nine-tick replans, and active ingestion remains pinned by the existing transaction guard. Food-before-drink priority, unsupported/rejected-source filtering, route attempt cadence, cooldowns, and route-budget behavior are unchanged. This is a local acquisition adaptation, not an SS14 random-selection parity claim; upstream utility still establishes the existing food distance preference (see `NPC-PARITY.md`).
