# Automatic atmosphere firelocks: manual checklist

- Build a **new** room with `/ms14 firelock_fixture build`, then `initialize` and `enter`. Use `scenarios` and `status`; see [FIXTURE.md](FIXTURE.md) for expected states.
- Apply `scenario pressure_danger`; wait for CLOSED, then apply `scenario combined_differential`. Check both warning channels, denied ordinary opening, and timed crowbar prying.
- With differential warnings active and closed, use `power off`: roof lighting and warnings should remain visible. Test empty-hand pry; restore with `power on`, then `reset`.
- Compare unpowered pry times: hands/crowbar take 3/1.5 seconds, or 4.5/2.25 seconds while environmentally locked.
- On an unpowered, unbolted ordinary airlock, try an empty-hand interaction: prying should take 15 seconds; a normal crowbar takes 1.5 seconds. Power restoration or changing hands should cancel hand prying. Bolted airlocks remain non-pryable in this port.
- Place a standalone standard firelock; also install one on an ordinary airlock.
- Inspect both faces and all four horizontal facings during motion. Fully open shutters should leave no top/bottom remnants.
- Walk through both doorway cells when open; check for stops or brief pauses. For an attached cover, both the base door and cover must be open.
- Operate the base airlock independently: moving it must not automatically operate the firelock.
- Exercise actual atmospheric hazards, obstruction, hand/tool prying and power loss/restoration. Warning alone must not deploy the firelock; powered Danger should. Normal reopening remains subject to environmental admission.
