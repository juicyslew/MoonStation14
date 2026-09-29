# SS14 bound UI survey — design references, not local feature commitments

Pinned upstream checkout: `space-wizards/space-station-14` commit `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. Paths below are repository-relative, verified against that checkout. XAML and C# illustrate structure/behavior; they are not code to copy. “Kit” means reusable client presentation and local interaction; each real machine still needs an independently authorized, server-owned state/intent contract.

| Family | Exact upstream reference | Visible pattern / contract boundary |
| --- | --- | --- |
| APC | `Content.Client/Power/APC/UI/ApcMenu.xaml`; `Content.Client/Power/APC/ApcBoundUserInterface.cs` | Fixed FancyWindow, 64×64 entity pane, two-column labels/switch, charge progress and footer. BUI sends breaker intent, updates state and access; local APC keeps its *own* authenticated menu/packet checks. |
| Chemistry | `Content.Client/Chemistry/UI/ChemMasterWindow.xaml` | Tabs, container/buffer lists, sort and transfer/discard toggles, eject; no reagent or item mutation without owner contract. |
| Vending | `Content.Client/VendingMachines/UI/VendingMachineMenu.xaml` | Search, browsable stock rows, footer; purchase/dispense is server-owned. |
| Power monitoring | `Content.Client/Power/PowerMonitoringWindow.xaml` | Status/value tables and navigation map; graph/map is specialty adapter, not fake live data. |
| Atmos pump | `Content.Client/Atmos/UI/GasPressurePumpWindow.xaml` | Switch, numeric pressure input, set/max actions, disabled state; values/limits require authoritative semantics. |
| Cargo | `Content.Client/Cargo/UI/CargoConsoleMenu.xaml` | Tabs, search, categories, scrolling requests/orders; currency, queue and ordering are server-owned. |
| Lathe | `Content.Client/Lathe/UI/LatheMenu.xaml` | Recipe search/category, numeric amount, queue/materials; reordering and presets require owner-defined intents/snapshots. |
| Health analyzer | `Content.Client/HealthAnalyzer/UI/HealthAnalyzerWindow.xaml` | Scrollable `HealthAnalyzerControl` patient report; rich labels and status from actual medical state only. |
| Storage / inventory | `Content.Client/Storage/StorageBoundUserInterface.cs`; `Content.Client/UserInterface/Systems/Storage/Controls/StorageWindow.cs` | Dynamic grid/slots, hover/drag and owner refresh. **Item transfer is not a local slot action**: require inventory owner, move validation and authoritative snapshots before enabling drag/drop. |

Common UI references: `Content.Client/UserInterface/Controls/FancyWindow.xaml.cs`, `Content.Client/UserInterface/Controls/SwitchButton.cs`, `Content.Client/UserInterface/Controls/SearchListContainer.cs`, `Content.Client/UserInterface/Controls/SlotControl.cs`. Style sources: `Content.Client/Stylesheets/Sheetlets/WindowSheetlet.cs`, `SwitchButtonSheetlet.cs`, `ButtonSheetlet.cs`, `ProgressBarSheetlet.cs`, `TabContainerSheetlet.cs`, `ItemListSheetlet.cs`, `ScrollbarSheetlet.cs`, `LineEditSheetlet.cs` (all under the same `Content.Client/Stylesheets/Sheetlets/` prefix). Distinguish these theme rules from control behavior and from bound UI/server actions.

## Applicability matrix

Legend: **A** = concrete local APC use; **R** = reusable client widget appropriate for that family; **C** = requires a separately specified authoritative owner before a gameplay action can work; **S** = specialty adapter boundary, not a generic working map/graph. Blank = not a representative need in this survey. These marks do not claim the corresponding machine is implemented locally.

| Family | Window chrome / footer / help | Button / toggle / switch / disabled / tooltip / focus | Label / status / values | Bar / meter | Tab / category / selector | Scroll / search / list / row | Text / numeric validation | Image / preview | Slot / icon / grid / drag | Queue / reorder / presets | Map / graphs |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| APC | A | A | A | A |  |  |  | A |  |  |  |
| ChemMaster | R | R,C | R | R | R | R | R | R | C | C |  |
| Vending | R | R,C | R |  | R | R |  | R | R,C |  |  |
| Power monitoring | R | R | R | R | R | R |  | R |  |  | S |
| Atmos pump | R | R,C | R | R |  |  | R,C |  |  |  |  |
| Cargo | R | R,C | R |  | R | R | R | R | R,C | C |  |
| Lathe | R | R,C | R | R | R | R | R,C | R | R,C | C |  |
| Health analyzer | R | R | R | R | R | R |  | R |  |  |  |
| Storage | R | R,C | R |  | R | R |  | R | C |  |  |

Kit coverage must include the entire column set as reusable interaction/presentation contracts, with graph/map deliberately reserved for adapters and item movement explicitly blocked until inventory integration. A themed static slot grid is not proof of working inventory. The catalog is a representative BUI survey, not authorization to port those devices.
