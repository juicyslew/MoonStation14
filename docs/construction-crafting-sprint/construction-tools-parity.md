# Construction tool prototype mapping

Source checked against SS14 checkout `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

- SS14 `Resources/Prototypes/Entities/Objects/base_item.yml` defines abstract `BaseItem`: its `Item.size` default is `Small` (lines 3–7), and it composes dynamic `Physics` plus fixtures and related item behavior (lines 26–46). This is the upstream composition/default, not a local typed physical-component contract.
- SS14 `Resources/Prototypes/Entities/Objects/Tools/tools.yml` declares `Screwdriver` (lines 47–89) and `Wrench` (91–128) as `BaseItem` children with `Tool` qualities `Screwing` and `Anchoring`. `welders.yml` declares `Welder` as a `BaseItem` child, with a 100-unit fuel solution, `Tool` quality `Welding`, and `Welder`/toggle-light components (lines 79–100).
- Local `construction_tool/base_tool.json` is only an inheritance node for local capability defaults: empty qualities and speed modifier 1.0. Wrench and screwdriver resolve their supported quality; Welder resolves Welding and the locally modeled fuel composition. These values are consumed by `ModTools`, anchoring/junction/welding actions, and `WelderFuelService`.
- `smallItem`, `dynamicPhysics`, and `anchored` are deliberately absent from the local tool prototype schema and JSON. Minecraft `ItemStack`/item registration supplies the item adaptation; the local prototype does not resolve, emulate, or claim SS14 `Item`, `Physics`, `Fixtures`, or `Transform` components. No physical-size, physics, or anchoring framework is added for these tools.
- Local Welder fuel values/toggling are a deliberate Minecraft runtime adaptation, not a claim of parity with SS14's reagent-solution and `Welder` component systems.
