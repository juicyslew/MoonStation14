# Radiation collector context menu manual check

1. Connect an authenticated client and control a body facing a collector; right-click it with no other UI open.
2. Confirm an empty collector produces no actionable Eject option.
3. Put a gas tank in the collector and repeat; confirm the localized Eject gas tank action appears.
4. Click Eject with both hands free; confirm authoritative inventory state updates.
5. Repeat while holding an item; confirm the server resolves available hands correctly.
6. Repeat with full hands; confirm no client-side optimistic transfer occurs.
7. Replace the collector tank before clicking a stale menu; confirm stale server proof rejects it.
8. Switch authority while the query is pending; confirm no delayed receipt opens a menu.

Authenticated connected-client behavior remains manually unverified.
