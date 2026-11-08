# Owner correction — first-person effects, power and prone use

Technical PASS `run_401c3efcf2ac4289bf173e32` received further owner feedback;
it is not acceptance. Preserve the eight approved artist deliveries and prior
loading, hand ownership, HUD, wield and conservation fixes.

## Work units and acceptance

1. **Effects:** retain the good third-person beam while making its firing-player
   view visible. Correct muzzle/impact source axes separately from travel. Correct
   moving projectile orientation through actual client motion and sprite consumers.
   Presentation must not alter authoritative traces, hit points or damage.
2. **Fire prediction:** prevent rapid-tap phantom/double gunshot feedback for Mosin
   and Inspector. Test pending and reconciled requests against their real cadence;
   preserve immediate valid-shot prediction and exactly-once server commitment.
3. **Power:** show standalone cell charge in held HUD and examination. Add the
   source-backed, powered weapon capacitor recharger: integral batteries remain
   integral; no invented cell slot or self-recharge. Reuse existing power allocation,
   item ownership and battery state, including a cell installed inside Svalinn.
4. **Casings:** replace short-lived cosmetic spent outputs with persistent,
   recoverable spent items through the shared loose-item path. Keep small textured
   presentation, landing sound and cleanup through ordinary item/disposal services.
   Spent items must never become live ammunition or duplicate outputs.
5. **Prone:** provide visible, directional firearm aiming through the existing
   prone/body/weapon transform owners. Keep authority, medical and turnover rules;
   stomach normalization for validated wielded guns is permitted where needed.
6. **Catalog:** remove Python's unsupported runtime identity and assets, not only its
   Creative entry. Test fixtures must use approved registered items.

The broader world-gun context-menu interaction and first-person context-menu design
are explicitly deferred to a separate focused sprint (owner items 4/4a).

## Verified SS14 boundaries

Pinned source is `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
`WeaponCapacitorRecharger` accepts the selected energy weapons and power cells;
`ChargerSystem` resolves the inserted item's battery or its installed cell. Default
charging is 20 J/s with 1 W passive draw, anchored and powered only. This owner
request supersedes the earlier exclusion of the minimal charging machine.

`PowerCellSystem` examines standalone cells as a rounded whole percentage. Ordinary
spent cartridges are real entities with no floor lifetime timer; the port's prior
cosmetic-only choice is superseded. No new janitor framework, context-menu redesign,
turbo/portable charger or passive gun regeneration is included.

Fresh focused checks, independent review and a complete replacement readiness gate
are required. CPU/resource evidence is not GPU or owner acceptance.
