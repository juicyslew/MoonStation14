# Stunned action admission

Authoritative body-hand and body world-use requests are admitted against the currently resolved controlled body (`CharacterControlSystem.canAct`) before invoking gameplay handlers. This prevents a stunned body from performing an item action, pickup/drop/equipment/storage mutation, or world interaction even when its carrier remains unstunned. Wire-panel opening and wire mutation use the same body status check; wire queries remain read-only and available.

This is an action-admission rule, not a control-authority invalidation: HUD, cursor, read-only queries, and chat/speech remain available. It also does not suppress passive contact electrocution. A valid wire CUT/MEND/PULSE can itself cause a shock after admission; the request is evaluated once at command start, and subsequent requests are rejected while the body remains stunned.

Parity intent: SS14 `SharedStunSystem.OnAttemptInteract` blocks active interactions (including use, pickup, drop, equip, throw, and attack), while speech is intentionally not blocked. Passive contact shocks are independent of that interaction gate. Local implementation reuses the existing `STUN_ACTION_BLOCK` status behavior rather than adding a second status mechanism.

The focused `stun_action_gate` GameTest exercises a real bound body: paralyze denies the existing final hand-selection admission without changing the live-hand revision, then expiry permits the same operation. `StunActionAdmissionBytecodeTest` locks the actual compiled request entrypoints: resolved-body admission precedes hand/block/wire effects, wire query-session validation remains ungated, and an accepted wire mutation is not rechecked after its own shock.
