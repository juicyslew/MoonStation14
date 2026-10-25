# Botany genetics fixture manual

Sprint closed and owner accepted on 2026-10-24; current scope and remaining limits are recorded in [HANDOFF.md](HANDOFF.md).

Requires an authorized operator and clear loaded area. Build with `/ms14 botany_fixture`; it creates the 52-bay fixture paused. Read the table while paused, then start one named set. `/ms14 botany_fixture start <set>` activates only that set; `pause` pauses it and `resume` restarts only the selected set. After a set, pause/inspect as needed, then start the next set. Do not start all care bays (19–40) together: use the smaller sets below. Pausing freezes fixture-tray automatic processing only; player care/harvest/sampling remain available. RNG stays stochastic. Pacing state is transient; reload is a state boundary.

| Set | Bays | Set | Bays |
|---|---|---|---|
| baseline | 01–02 | core | 03–04 |
| mutagen | 05–07 | harvest | 08–10 |
| sampling | 11–13 | resources | 01, 14–15 |
| timing | 01, 11, 16–18 | care-health | 19–22 |
| care-genes | 23–25 | care-growth | 26–29 |
| traits | 02, 30–32 | toxins | 33–34 |
| pests | 35–36 | weeds | 37–38 |
| heat | 39–40 | viability | 41–42 |
| lifespan | 43–44 | pressure | 45–46 |
| products | 47–50 | gases | 51–52 |

**Before start (important):** trays are intentionally preset, not naturally grown controls. The adverse starting conditions are called out on each relevant sign; care demonstrations have adequate water/nutrients (bay 19 has nutrition 80 for fertilizer headroom), and tolerance demonstrations start healthy with full resources. Do not alter a specimen before its stated comparison. Presets demonstrate starting traits, never acquired mutations. 01 is the ordinary wheat control for 14/15/18; preserve it unharvested through those comparisons. 02 is the ordinary tomato control for 30. Preset specimens do not acquire mutation provenance. Do not directly dose the preset stations and call that an acquired mutation.

Use Shift-left-click examination within three blocks. Stations are numbered in canonical serpentine walking order: 01–18, 19–40, 41–52. Read each row as the action and ordinary observable to expect; compare at the same stated cycle/time. 01 must run with 14/15/18 for resource/temperature comparisons; 02 with 30 for production; 11 with 16 for stage comparison. A missing-gas warning is not proof of successful gas exchange. Stations 07, 51, and 52 have **no ordinary direct success cue**; do not expect a HUD, counter, or success message. Optional diagnostic inspection is not ordinary gameplay proof. Packet potency metadata is not a trustworthy visual readout of hidden genetics. Crop world presentation maps potency 0/5/10/50/100/150 to scale 0.5/0.7/0.9/2.5/4.5/6.5 (unclamped). A ripe plant can still mutate; health drop alone does not diagnose mutation. Ligneous means woody plant and requires a sharp tool to harvest.

| Bay | Purpose; do this | Observable / comparison |
|---:|---|---|
| 01 | Baseline wheat; run alongside 14/15/18, examine and leave untouched. | Resource and ideal-heat control; do not harvest early. |
| 02 | Baseline tomato; run alongside 30 and harvest both. | Ordinary repeat tomato control for 30. |
| 03 | Healthy wheat, full water/nutrients; use hoe. | Weeds clear; watering/nutrients are not the demonstration. |
| 04 | Partial-care warning; try water/nutrients. | Ordinary warnings; not a mutation test. |
| 05 | Vestine chemistry plant; wait, examine, harvest and grind. | Actual beaker contents; no inspector-only proof. |
| 06 | Mutagen trigger; dose normal 5u unstable mutagen. | Examine plant; mutation is probabilistic, not guaranteed. |
| 07 | Preset nitrogen/oxygen gas genes; wait/examine. | No direct ordinary success cue. |
| 08 | No-repeat wheat; harvest once. | Plant clears; control for harvest modes. |
| 09 | Repeat wheat; harvest once. | Plant remains. |
| 10 | Self-harvest wheat; wait. | Drops appear without harvesting. |
| 11 | Screaming wheat sample; wirecutter once. | Packet and scream; packet may land in hand/world. |
| 12 | Yield preset; harvest and extract. | Packet yield 7, potency 5. |
| 13 | Potency preset; harvest and extract. | Packet yield 3, potency metadata 50; this is not a direct ordinary genetic readout. |
| 14 | Water-use preset; wait/examine against 01. | Water trend differs from control. |
| 15 | Nutrient-use preset; wait/examine against 01. | Nutrient trend differs from control. |
| 16 | Fast maturation preset; run alongside 11 and compare stage. | Stage projection differs; both are not ready. |
| 17 | Low endurance preset; sample once. | Health warning; ordinary warning is not direct gene diagnosis. |
| 18 | Ideal-heat preset; wait/examine against 01. | Room-temperature warning relative to control. |
| 19 | Use station sign for reagent/dose; paused dose, then start care-health alone. | Starts ~50% health, nutrition 80, with ammonia adversity. Dose may heal/alter nutrition; ordinary examine cannot confirm chemistry. |
| 20 | Use station sign for reagent/dose; paused dose, then start care-health alone. | Targets toxins (39); effects/probability are normal, removal not guaranteed. |
| 21 | Use station sign for reagent/dose; paused dose, then start care-health alone. | Targets pests (6); effects are probabilistic. |
| 22 | Use station sign for reagent/dose; paused dose, then start care-health alone. | Targets weeds (6); effects are probabilistic. |
| 23 | Sedin potency bottle; dose. | Potency may fall; restoration roll is a no-op here. |
| 24 | Robust-harvest bottle; dose. | Potency rises; yield may fall/seedlessness may begin. |
| 25 | Use station sign for reagent/dose; paused dose, then start care-health alone. | Starts ~50% health, age maturation+10, toxins 9; resources 100. Changes are conditional. |
| 26 | Diethylamine growth bottle; dose. | Growth advances; repeat may be needed. |
| 27 | Diethylamine genes bottle; dose. | Two independent gene rolls; ordinary inspection cannot verify 10% change. |
| 28 | Use station sign for reagent/dose; paused dose, then start care-health alone. | Starts healthy (100%) but unviable; ordinary examination may not confirm viability. |
| 29 | Use station sign for reagent/dose; pause, dose, start care-health alone; harvest/extract fresh crop. | Healthy, mature, ready but seedless. Successful packet proves restoration; compare potency with 23/24 if appropriate. |
| 30 | Early-production repeat tomato; harvest 02 and 30, then wait. | 30 readies earlier than baseline 02. |
| 31 | Low-pressure tolerance; wait/examine. | Room warning against 01. |
| 32 | Ligneous wheat; try hand harvest, then sharp knife. | Hand harvest denied; knife succeeds. |
| 33 | Toxin tolerance 4; run with 34. | Full health/resources; matched exposure, damage is conditional over time. |
| 34 | Toxin tolerance 10 control; run with 33 through first ordinary cycle. | Lives; paired with 33. |
| 35 | Pest tolerance 5; run with 36. | Full health/resources; matched exposure, damage is conditional over time. |
| 36 | Pest tolerance 15 control; run with 35 through first ordinary cycle. | Lives; paired with 35. |
| 37 | Weed tolerance 5; run with 38. | Full health/resources; matched exposure, damage is conditional over time. |
| 38 | Weed tolerance 15 control; run with 37 through first ordinary cycle. | Lives; paired with 37. |
| 39 | Heat tolerance 10; run with 40. | Full health/resources; matched exposure, damage is conditional over time. |
| 40 | Heat tolerance 25 control; run with 39 through first ordinary cycle. | Lives; paired with 39. |
| 41 | Viable wheat control; run with 42 through first ordinary cycle. | Lives; compare 42. |
| 42 | Unviable wheat preset; compare with 41 over time. | Visible viability warning; gradual health loss after start, not instant death. |
| 43 | Lifespan 25 control; wait/examine. | Alive at matched age; compare 44. |
| 44 | Lifespan 5 preset; compare with aged healthy 43 over time. | Old-age stress may develop gradually; not first-cycle death. |
| 45 | Maximum-pressure 121 control; wait/examine. | Alive in room; compare 46. |
| 46 | Maximum-pressure 85 preset; wait/examine. | Room-pressure warning/death relative to 45. |
| 47 | Slippery produce; harvest, drop, walk over it. | Contact slip/stun/sound response. |
| 48 | Chemical preset; harvest, grind, examine beaker. | Actual reagent in beaker; no direct success cue before grinding. |
| 49 | Wheat source species; harvest and inspect product identity. | Wheat identity; source for 50. |
| 50 | Bay now grows the real meat-wheat crop; harvest `MEATWHEAT_BUSHEL`. Extract/replant its seed-bearing produce, or use the bushel in hand with Z once. | Properly named meat-wheat bushel is Produce; use-in-hand Z converts one use to existing `FoodMeatWheat` clump, which is food but not Produce and has no seed capability. |
| 51 | Nitrogen consume-only gene; wait/examine. | No direct ordinary gas-success cue. |
| 52 | Oxygen exude-only gene; wait/examine. | No direct ordinary gas-success cue. |

Physical signs are short reminders; this table contains full actions and limits. See [showcase coverage](SHOWCASE-COVERAGE.md) for mutation-to-bay index and [PARITY](PARITY.md) for upstream mapping. No visual readability or owner acceptance is claimed until reviewed.

**Current interaction, feedback and UI notes:** hold a beaker and press E on the tray to transfer solution; do not drag-pour it. Pinned SS14's tray has `RefillableSolution`, not `DumpableSolution`: ordinary drag-pour requires Dumpable via `SolutionDumpingSystem`, while held-use AfterInteract is handled by `SolutionTransferSystem`. No new drag-pour behavior is implemented. Crop presentation bounds are 0.5–4.5; the mapped scales are above. Only presentation/world crop draw-outline changed via the actual baked flat pose; physical picking, reach, collision and movement are unchanged. Generic typed defaults 1–2 do not alter this. Ordinary hand labels are 12px; 27px applies only to the actual active-ammo row, not botany labels (regression fix only). For examination, normal Shift-examine may omit the tray when a concrete 32-candidate pile cap excludes it; the fixed common selection retains the nearest already-visible block alongside items, and Shift-scroll can reach the tray. Many-trait normal response-codec and repeated-admission tests were added. The owner-specific popup root remains **unconfirmed**; do not attribute the issue to response length or claim developer-command pagination changed. Ordinary mutation trait notices are not full chemical scans.
