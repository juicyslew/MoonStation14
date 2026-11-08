# Jug and beaker liquid policies

Jug and beaker typed prototypes project the supported parts of SS14 container
composition into resolved `refillableSolution`, `solutionTransfer`, and
`drinkSolution` policies. Their nested fields use the same field-aware parent
merge as solution containers, so child overrides remain independent. Missing
policies default disabled; explicit `enabled` is necessary to opt into behavior.
The item trait getters expose those resolved policies to runtime consumers.

SS14 reference: pinned `space-station-14` revision
`c9df5ef5d675b0d1d226828bddf6b78c28502d91`, BaseItem/Jug and BaseBeaker
compositions. Transfer amount and drink amount project the 5u defaults;
drink personal delay projects 1s as 20 ticks, and an omitted edible solution
name retains SS14's `food` component default (base Jugs and Beakers explicitly
select `drink` and `beaker`, respectively). Refill caps are optional. The
local policy does not invent editable transfer-amount controls, force-feed
behavior, utensil handling, or drain/refill do-after timing where no runtime
operation supports them. Existing drain and primary-solution getters remain
unchanged.

The legacy local `BottleItem` retains its pre-policy live-hand transfer
capability through typed default-solution getters. This is a compatibility
projection of the existing Minecraft item, not a claim that SS14 BaseItem or
BaseBeaker has this bottle composition; new typed prototypes remain disabled
unless their resolved policies opt in.
