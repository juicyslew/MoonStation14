# Tool prototype mapping

Verified against SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. The standard tool
chain is `BaseItem` → tool item, with `Tool` qualities: screwdriver uses
`Screwing`, wirecutter uses `Cutting`, and multitool uses `Pulsing`. Crowbar
inherits `BaseCrowbar` → `BaseItem`; the base supplies `Tool` quality `Prying`
and `PryingComponent` with `speedModifier: 1`. Local JSON composes the supported
quality and prying fields through abstract `base_item` and `base_crowbar`
profiles. Prying consumer defaults are 5 seconds base, modified by the tool
speed (powered door operation 3 seconds); panel wiring consumes its own 1-second
delay. These are behavior references, not extra local component claims.

Sources: `Resources/Prototypes/Entities/Objects/Tools/crowbar.yml`,
`crowbars.yml`, `tools.yml` (Wirecutter/Screwdriver), `multitool.yml`; `Content.Server/Tools/Systems/ToolSystem.cs`,
`Content.Server/Tools/Systems/PryingSystem.cs`, and `Content.Server/Doors/Systems/AirlockSystem.Wires.cs`.
Local `wirecutter` maps the existing Minecraft wirecutter to `CUTTING` as an
adaptation; its established cable-cutting action remains separate. Unsupported
SS14 base-item fields/components and general tool-use delay policy are deliberately
not modeled. The optional `useSound` field carries only the verified Wirecutter
sound and is consumed by the airlock wires action after accepted cut/mend; pulse,
rejected and stale commands stay silent. Screwdriver has no Tool useSound override
and its panel open/close completion sounds are separate WiresPanel feedback. Local
tool consumers use the resolved catalog; unknown item
IDs must fail closed.

The pinned standard Crowbar chain is `Crowbar -> BaseCrowbar -> BaseItem`; its
Tool quality is Prying, base prying speed is 1, and `Tool.useSound` is
`/Audio/Items/crowbar.ogg`. Wirecutter and Screwdriver each directly inherit
BaseItem; Wirecutter has Cutting quality and `/Audio/Items/wirecutter.ogg` use
sound, while Screwdriver has Screwing quality and no Tool `useSound` override.
Local `ToolProfile` resolves Wirecutter's item-use sound for cut/mend feedback;
Screwdriver receives no fabricated item-use response. Panel open/close feedback
is instead resolved from the supported WiresPanel sound defaults and only played
on successful timed toggle completion; see `ASSETS.md`.
