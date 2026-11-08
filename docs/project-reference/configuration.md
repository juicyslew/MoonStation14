# Configuration notes

These entries describe the current runtime, not switches users should set:

| Setting | Current behavior |
| --- | --- |
| `experimentalVerticalSliceMovement` | Retired. The legacy native-player route and its configuration switch are deleted. Ordinary native-player movement uses Minecraft's normal route; this does not mean the shared `CharacterMovementMotor` or its policy/prediction support was deleted. |
| `experimentalMindGhostControl` | Fixed enabled (`true`). This does not create a separate lifecycle opt-in or automatically enroll every player: existing configured lifecycle eligibility and workflow still apply. |
| `enableAtmospherics` | Fixed enabled (`true`). `atmosphereVacuumDimensions` remains relevant to ambient vacuum/dimension behavior. |
| `moonSky` | Fixed enabled (`true`), client-local and rendered for the Overworld; no setting is needed. It is visual and does not control atmospheric simulation. |
| `enablePowerSimulation` | Fixed enabled (`true`). Device-specific commands such as `TurnoffDevice` remain distinct and are not simulation switches. |

Historical sprint handoffs and validation records retain the configuration and
results that applied when written; they are not current setup instructions.

All five entries are removed from the config spec and local run TOML, not
implemented as constant-returning startup gates. Native movement controllers,
protocol, ownership mixins, and dedicated tests are removed; shared body-control
motor/prediction remains. Simulation-off branches and dedicated tests are removed.
Paid ventilation now tests the automatic live consumer rather than manually
claiming budgets left unused by globally disabled atmospherics.

## Validation checkpoint

- Compilation and source review pass; no retired flag/gate references remain in Java.
- Unit suite: **2,110 passed**, zero failures (`build/flag-retirement-final-unit.log`).
- Focused `simulation-defaults`: **5/534 selected and executed**, **2 passed / 3 failed**.
- Full GameTests: **534/534 selected and executed**, **527 passed / 7 failed**; zero untagged.
  See `build/flag-retirement-final-full.log` and `build/flag-retirement-focused-final.log`.
- Failures: `builtfixtureinitializesandreportsresolvedruntime`,
  `serviceexchangeisatomicandconservesfinitecellgasandenergy`,
  `onewireddebuglampstaysbelowtripthreshold`,
  `poweredventmetertracksbreakerandreplacement`,
  `poweredscrubbercapturesonlyselectedgas`,
  `onlycompletedpaidsolvemovesconservedgas`, and `disableddevicedoesnotmovegas`.
  Causes remain unresolved; do not infer regression attribution from test names.
  Failed fixture artifacts are retained in `build/gametest-tags-fixture`.

Flag removal is implemented, but runtime regression acceptance is pending.
The critical/death/prone sprint remains unaccepted and uncommitted.
