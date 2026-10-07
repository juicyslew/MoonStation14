# Connected disposal checks

Use the same updated jar on client and server. Keys below are defaults; check Controls if rebound.

1. Build a fresh disposal fixture. Its build result should report initialized finite air; you should not take pressure/breathing damage while standing inside.
2. Aim at the APC, then the unit, and press **E**, both empty-handed and holding an item. Each should open its own controls, not spectator inventory.
3. Left-click the unit with a held item. The stack should leave your hand and produce one insertion sound, audible to you and nearby players.
4. Aim at the unit and press **K**; remain still for 0.5 s. Check the actionbar, locked automatic rear third-person camera (**F5**), and **E** controls. If roof/free movement persists, report the exact actionbar text or server `cancel/refused` line.
5. While inside, open the unit controls, restore power and flush through a ready connected route. Verify ordinary control after ejection.

Latest movement batch: check that the ride is smoother and the open outlet carries you forward before stopping. While contained, idle run mode must not eject you; sleeping/stun must block escape. True critical health state is not yet implemented.

### Self-entry follow-up

Direct K entry resolves to 0.5 s independent of power. Its elapsed clock is the captured body's level game time, advanced once from `ServerTickEvent.Post` for online actors; it is deliberately not driven by a player/body physical tick or UI request. Server logs start and completion (body/unit/timing/owner binding) without per-tick spam. The pinned upstream movement threshold is 0.3 with strict distance comparison (`RobustMapCoord.InRange`); the local policy maps full 3D movement and permits exact threshold, block-edge, and 1 mm settling cases. It has no block-position equality, hands-revision, or hand dependency. Native single-hit interruption is an actual post-resistance typed damage event totaling at least 1: healing, tiny poison, and accumulated tiny hits do not cancel. The neutral damage event represents a positive canonical committed damage amount; direct/menu-pending interactions cancel through the menu listener. Actionbar start/cancel includes cause, and completion is emitted only after owner binding. Server cancellation/refusal diagnostics include body UUID, unit position, and reason.

Focused native camera-tour tests use trusted insertion, not authenticated K input. Thus connected K roof/free-movement behavior remains **unverified**; these parity fixes remove known abort causes and aid diagnosis, but do not establish that the reported symptom is fixed or reproduced. No user handoff has been completed.
