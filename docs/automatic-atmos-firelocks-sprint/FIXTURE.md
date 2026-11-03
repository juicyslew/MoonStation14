# Automatic-atmos firelock fixture

Admin-only. Move to a clear, loaded area and build a new fixture beside your controlled body.
No coordinates are needed; follow-up commands remember your site. The footprint is 20×12×5.
Build a new fixture to obtain the roof lighting; reset does not rebuild an older fixture.
Keep it loaded: sensing and alarm relays use the ordinary simulation, not synthetic events.

```text
/ms14 firelock_fixture build
/ms14 firelock_fixture initialize
/ms14 firelock_fixture status
/ms14 firelock_fixture enter
/ms14 firelock_fixture scenarios
/ms14 firelock_fixture scenario pressure_warning
/ms14 firelock_fixture scenario pressure_danger
/ms14 firelock_fixture scenario temperature_warning
/ms14 firelock_fixture scenario temperature_danger
/ms14 firelock_fixture scenario pressure_differential
/ms14 firelock_fixture scenario temperature_differential
/ms14 firelock_fixture scenario combined_differential
/ms14 firelock_fixture reset
/ms14 firelock_fixture power off
/ms14 firelock_fixture power on
```

After build, wait for ownership and retry `initialize` if endpoints are not ready. Initialization
is once-only; it installs normal breathable finite gas, not proof that any device has received
paid power. Scenario/reset are bounded trusted operator setup writes of finite mixtures using
the resolved current cell volume; this is a deliberate adaptation for repeatable admin tests,
not a gameplay source, forced alarm severity, receiver dispatch, or automatic reseed/tick source.
Use `enter` to move your controlled body into the sealed source room without breaking its walls.
Wait for normal alarm/device/power loops and inspect status: warning must not close the door,
danger should close it, and reset should allow normal admission logic to reopen it. With the door
closed, its two blocked doorway cells are validated but not written; passable owned cells are
normalized. Test obstruction admission, pry behavior, and traversal manually. Keep the footprint
clear and loaded; do not treat command success as proof of paid power or door response.

`scenarios` lists the command's actual supported scenario set. Differential scenarios require the
existing door to be fully CLOSED; if open, first apply `pressure_danger`, wait for CLOSED, then
apply the requested differential. They write the left 101.3 kPa/293.15 K cells and right-side
450 kPa and/or 700 K cells, preserving finite oxygen/nitrogen gas and using each owned cell's
resolved volume. Reset restores normal gas on both sides and relies on ordinary alarm admission;
it does not force a warning or door phase. Fixture roofs use constant SEA_LANTERN blocks at
relative (2,3,4), (6,3,4), and (10,3,4), a test-only lighting adaptation independent of APC power.

## What to observe

| Setup | Expected result |
| --- | --- |
| `reset` / `scenario normal` | Normal gas; reopening remains subject to power and local admission. |
| `pressure_warning` / `temperature_warning` | Alarm Warning, no automatic closure or local differential lock. |
| `pressure_danger` / `temperature_danger` | Powered alarm closes the door. Equal conditions on both sides do not light differential warnings or prevent ordinary opening; Danger may close it again. |
| Fully closed, then `pressure_differential` | Pressure warning/light; ordinary opening denied. |
| Fully closed, then `temperature_differential` | Temperature warning/light; ordinary opening denied. |
| Fully closed, then `combined_differential` | Both warning channels; ordinary opening denied. |
| `power off` while open or closed | Ordinary powered opening denied. Closed differential warnings remain visible. Roof lights stay on. |
| Empty-hand interaction while known unpowered | Timed hand pry, not an instant powered opening. |
| Crowbar interaction on a powered locked door | Timed strong-tool pry; successful pry gives a two-second emergency-close cooldown. |
| Stand in the open doorway, then apply Danger | Observe actual obstruction/crush handling and motion; use a disposable test session. |

Use `status` to compare alarm severity with **local** pressure/temperature warnings and side deltas.
It distinguishes unknown power/samples from known readings. Opening, closing, denied interaction,
pry progress and cooldown are observed through ordinary interactions, never forced by commands.
This room does not reproduce the Space-only emergency solver path (covered separately by native
Space tests). Welded and bolted firelock states are not supported by this port.

## EVA manual fixture

`/ms14 eva_fixture build` creates a 9×6×9 sealed room adjacent to the authorized controlled body;
then use `initialize`, `enter`, `status`, `scenario normal|vacuum|cold|hot|contaminated`,
`reset`, and `kit [helmet|suit|mask|tank|emergency_tank]`. It remembers the last successful site
per operator and dimension in runtime memory only. After a server restart, build a new fixture;
no site persistence or fixture gas/tank/body-state restoration is provided. The room is self-contained
with no APC, door, or opening; `enter` relocates the existing authorized body into its interior.
Initialization/scenarios wait for and write only the complete bounded finite-owned interior cell set.
Vacuum means zero actual room moles; cold/hot are finite gas setup temperatures, not forced damage.
`kit` grants ordinary items to body hands/world for manual equipping and internals connection; status,
reset, and scenarios never refill tanks, force equipment/connection, or alter body health.
